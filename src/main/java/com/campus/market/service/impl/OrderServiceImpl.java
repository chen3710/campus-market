package com.campus.market.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.market.common.BusinessException;
import com.campus.market.common.PageResult;
import com.campus.market.common.ResultCode;
import com.campus.market.dto.OrderCreateRequest;
import com.campus.market.entity.CreditLog;
import com.campus.market.entity.Product;
import com.campus.market.entity.ProductOrder;
import com.campus.market.entity.User;
import com.campus.market.enums.OrderStatus;
import com.campus.market.enums.ProductStatus;
import com.campus.market.mapper.CreditLogMapper;
import com.campus.market.mapper.ProductMapper;
import com.campus.market.mapper.ProductOrderMapper;
import com.campus.market.mapper.UserMapper;
import com.campus.market.service.OrderService;
import com.campus.market.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    /** 交易完成，买卖双方各加的分 */
    private static final int CREDIT_TRADE_BONUS = 2;
    /** 卖家已确认后买家反悔取消，买家扣的分 */
    private static final int CREDIT_BUYER_CANCEL_PENALTY = 3;
    /** 卖家单方面取消，卖家扣的分 */
    private static final int CREDIT_SELLER_CANCEL_PENALTY = 2;

    private final ProductMapper productMapper;
    private final ProductOrderMapper orderMapper;
    private final UserMapper userMapper;
    private final CreditLogMapper creditLogMapper;
    /** 单笔超时取消的执行者。抽成独立 Bean 是为了让 @Transactional 真正生效，详见该类注释。 */
    private final OrderTimeoutCanceller timeoutCanceller;

    // ------------------------------------------------------------------
    // 下单：整个项目最核心的一段
    // ------------------------------------------------------------------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO create(Long buyerId, OrderCreateRequest request) {
        Product product = productMapper.selectById(request.productId());
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或已被删除");
        }
        if (product.getSellerId().equals(buyerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "不能购买自己发布的商品");
        }
        if (!ProductStatus.ON_SALE.name().equals(product.getStatus())) {
            throw new BusinessException(ResultCode.CONFLICT, "该商品当前不可购买");
        }

        // ============ 防超卖的关键 ============
        // 把「判断在售」和「锁定商品」压缩成一条带条件的 UPDATE。
        // InnoDB 会对这一行加排他锁，并发的第二个事务会阻塞等待；
        // 等第一个事务提交后再做「当前读」，此时 status 已是 LOCKED，
        // WHERE 条件不成立 -> 影响行数为 0 -> 下单失败。
        // 这就是用数据库的行锁 + CAS 实现原子占位，比先 SELECT 再 UPDATE 可靠得多
        // （后者在两次操作之间留有竞态窗口，两个线程可能都读到 ON_SALE）。
        int locked = productMapper.casUpdateStatusWithVersion(
                product.getId(),
                ProductStatus.ON_SALE.name(),
                ProductStatus.LOCKED.name(),
                product.getVersion());
        if (locked == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "手慢了，该商品已被抢先下单");
        }

        ProductOrder order = new ProductOrder();
        // 雪花算法生成订单号：趋势递增、全局唯一，不依赖数据库自增，方便将来分库分表
        order.setOrderNo("CM" + IdWorker.getId());
        order.setProductId(product.getId());
        // 冗余快照：卖家之后改标题/改价都不影响已成交订单的展示与对账
        order.setProductTitle(product.getTitle());
        order.setProductCover(product.getCover());
        order.setBuyerId(buyerId);
        order.setSellerId(product.getSellerId());
        order.setAmount(product.getPrice());
        order.setStatus(OrderStatus.CREATED.name());
        order.setRemark(request.remark());
        order.setContact(request.contact());
        orderMapper.insert(order);

        return toVO(order, buyerId, Map.of());
    }

    // ------------------------------------------------------------------
    // 卖家确认
    // ------------------------------------------------------------------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirm(Long userId, Long orderId) {
        ProductOrder order = requireOrder(orderId);
        if (!order.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有卖家可以确认订单");
        }
        OrderStatus from = parseStatus(order.getStatus());
        // 状态机先拦截非法流转，避免用 if-else 堆砌判断
        from.assertTransferTo(OrderStatus.CONFIRMED);

        int updated = orderMapper.update(null, new LambdaUpdateWrapper<ProductOrder>()
                .eq(ProductOrder::getId, orderId)
                .eq(ProductOrder::getStatus, from.name())
                .set(ProductOrder::getStatus, OrderStatus.CONFIRMED.name())
                .set(ProductOrder::getConfirmTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "订单状态已变更，请刷新后重试");
        }
    }

    // ------------------------------------------------------------------
    // 买家确认完成 -> 商品真正售出 -> 双方信用分 +2
    // ------------------------------------------------------------------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long userId, Long orderId) {
        ProductOrder order = requireOrder(orderId);
        if (!order.getBuyerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有买家可以确认完成交易");
        }
        OrderStatus from = parseStatus(order.getStatus());
        from.assertTransferTo(OrderStatus.FINISHED);

        int updated = orderMapper.update(null, new LambdaUpdateWrapper<ProductOrder>()
                .eq(ProductOrder::getId, orderId)
                .eq(ProductOrder::getStatus, from.name())
                .set(ProductOrder::getStatus, OrderStatus.FINISHED.name())
                .set(ProductOrder::getFinishTime, LocalDateTime.now()));
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "订单状态已变更，请刷新后重试");
        }

        productMapper.casUpdateStatus(order.getProductId(),
                ProductStatus.LOCKED.name(), ProductStatus.SOLD.name());

        changeCredit(order.getBuyerId(), CREDIT_TRADE_BONUS, "完成交易", orderId);
        changeCredit(order.getSellerId(), CREDIT_TRADE_BONUS, "完成交易", orderId);
    }

    // ------------------------------------------------------------------
    // 取消：释放商品 + 按角色扣信用分
    // ------------------------------------------------------------------
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, Long orderId, String reason) {
        ProductOrder order = requireOrder(orderId);
        boolean isBuyer = order.getBuyerId().equals(userId);
        boolean isSeller = order.getSellerId().equals(userId);
        if (!isBuyer && !isSeller) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作该订单");
        }

        OrderStatus from = parseStatus(order.getStatus());
        from.assertTransferTo(OrderStatus.CANCELLED);

        // CAS 取消：带上 status 条件，避免把「读到的状态」和「写回的状态」之间
        // 发生的变更覆盖掉（比如卖家在这两步之间点了确认）。
        int updated = orderMapper.casCancel(orderId, from.name(), LocalDateTime.now(), reason);
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "订单状态已变更，请刷新后重试");
        }

        // 商品重新上架。这里同样用 CAS：只有当前确实是 LOCKED 才改回 ON_SALE，
        // 防止把已经 SOLD 的商品错误地改回在售。
        productMapper.casUpdateStatus(order.getProductId(),
                ProductStatus.LOCKED.name(), ProductStatus.ON_SALE.name());

        if (isBuyer && from == OrderStatus.CONFIRMED) {
            // 卖家都确认了才反悔，属于违约，要扣分
            changeCredit(userId, -CREDIT_BUYER_CANCEL_PENALTY, "卖家已确认后取消订单", orderId);
        } else if (isSeller) {
            changeCredit(userId, -CREDIT_SELLER_CANCEL_PENALTY, "卖家取消订单", orderId);
        }
    }

    // ------------------------------------------------------------------
    // 超时自动取消：由定时任务调用，不是用户触发的接口
    // ------------------------------------------------------------------
    @Override
    public int cancelTimeoutOrders(LocalDateTime deadline, int batchSize) {
        List<ProductOrder> expired = orderMapper.selectTimeoutOrders(deadline, batchSize);
        if (expired.isEmpty()) {
            return 0;
        }

        int cancelled = 0;
        for (ProductOrder order : expired) {
            try {
                // 逐笔走独立事务。这里的返回值很关键：false 说明订单状态
                // 在扫描之后、取消之前被用户改掉了（比如刚点了确认），
                // 属于正常竞争，静默跳过即可，不是错误。
                if (timeoutCanceller.cancelOne(order)) {
                    cancelled++;
                }
            } catch (Exception e) {
                // 单笔失败不能拖累同批其他订单；记下日志，下一轮扫描会重新捞到它
                log.warn("订单 {} 超时取消失败，留待下一轮重试", order.getId(), e);
            }
        }

        log.info("超时扫描：捞出 {} 笔，实际取消 {} 笔", expired.size(), cancelled);
        return cancelled;
    }

    @Override
    public OrderVO detail(Long userId, Long orderId) {
        ProductOrder order = requireOrder(orderId);
        if (!order.getBuyerId().equals(userId) && !order.getSellerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该订单");
        }
        return enrich(List.of(order), userId).get(0);
    }

    @Override
    public PageResult<OrderVO> pageBought(Long userId, long page, long size) {
        Page<ProductOrder> result = orderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductOrder>()
                        .eq(ProductOrder::getBuyerId, userId)
                        .orderByDesc(ProductOrder::getCreateTime));
        return toPage(result, userId);
    }

    @Override
    public PageResult<OrderVO> pageSold(Long userId, long page, long size) {
        Page<ProductOrder> result = orderMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<ProductOrder>()
                        .eq(ProductOrder::getSellerId, userId)
                        .orderByDesc(ProductOrder::getCreateTime));
        return toPage(result, userId);
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private PageResult<OrderVO> toPage(IPage<ProductOrder> source, Long viewerId) {
        PageResult<OrderVO> result = new PageResult<>();
        result.setRecords(enrich(source.getRecords(), viewerId));
        result.setTotal(source.getTotal());
        result.setPage(source.getCurrent());
        result.setSize(source.getSize());
        result.setPages(source.getPages());
        return result;
    }

    /**
     * 批量补齐买卖双方昵称：一次 selectBatchIds 拿到所有相关用户，
     * 而不是每笔订单查一次用户表（N+1）。
     */
    private List<OrderVO> enrich(List<ProductOrder> orders, Long viewerId) {
        if (orders == null || orders.isEmpty()) {
            return List.of();
        }
        Set<Long> userIds = new HashSet<>();
        orders.forEach(o -> {
            userIds.add(o.getBuyerId());
            userIds.add(o.getSellerId());
        });
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return orders.stream().map(order -> {
            OrderVO vo = toVO(order, viewerId, userMap);
            User buyer = userMap.get(order.getBuyerId());
            User seller = userMap.get(order.getSellerId());
            vo.setBuyerNickname(buyer == null ? "已注销用户" : buyer.getNickname());
            vo.setSellerNickname(seller == null ? "已注销用户" : seller.getNickname());
            return vo;
        }).collect(Collectors.toList());
    }

    private OrderVO toVO(ProductOrder order, Long viewerId, Map<Long, User> userMap) {
        OrderStatus status = parseStatus(order.getStatus());

        OrderVO vo = new OrderVO();
        vo.setId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setProductId(order.getProductId());
        vo.setProductTitle(order.getProductTitle());
        vo.setProductCover(order.getProductCover());
        vo.setBuyerId(order.getBuyerId());
        vo.setSellerId(order.getSellerId());
        vo.setAmount(order.getAmount());
        vo.setStatus(status.name());
        vo.setStatusDesc(status.getDesc());
        vo.setRemark(order.getRemark());
        vo.setContact(order.getContact());
        vo.setCancelReason(order.getCancelReason());
        vo.setCreateTime(order.getCreateTime());
        vo.setConfirmTime(order.getConfirmTime());
        vo.setFinishTime(order.getFinishTime());
        vo.setCancelTime(order.getCancelTime());

        User buyer = userMap.get(order.getBuyerId());
        User seller = userMap.get(order.getSellerId());
        if (buyer != null) {
            vo.setBuyerNickname(buyer.getNickname());
        }
        if (seller != null) {
            vo.setSellerNickname(seller.getNickname());
        }

        boolean isBuyer = order.getBuyerId().equals(viewerId);
        boolean isSeller = order.getSellerId().equals(viewerId);
        vo.setViewerRole(isBuyer ? "BUYER" : (isSeller ? "SELLER" : null));
        vo.setActions(resolveActions(status, isBuyer, isSeller));
        return vo;
    }

    /**
     * 由状态机推导出「当前用户在当前状态下能做哪些操作」，
     * 前端直接照着渲染按钮，不用在前端再抄一份状态判断逻辑。
     */
    private List<OrderVO.Action> resolveActions(OrderStatus status, boolean isBuyer, boolean isSeller) {
        List<OrderVO.Action> actions = new ArrayList<>();
        if (status.isFinal()) {
            return actions;
        }
        if (status == OrderStatus.CREATED && isSeller && status.canTransferTo(OrderStatus.CONFIRMED)) {
            actions.add(new OrderVO.Action("confirm", "确认订单", true));
        }
        if (status == OrderStatus.CONFIRMED && isBuyer && status.canTransferTo(OrderStatus.FINISHED)) {
            actions.add(new OrderVO.Action("finish", "确认完成交易", true));
        }
        if ((isBuyer || isSeller) && status.canTransferTo(OrderStatus.CANCELLED)) {
            actions.add(new OrderVO.Action("cancel", "取消订单", false));
        }
        return actions;
    }

    private void changeCredit(Long userId, int delta, String reason, Long orderId) {
        // 单条 SQL 完成增减，避免读-改-写导致的并发覆盖
        userMapper.addCreditScore(userId, delta);

        CreditLog log = new CreditLog();
        log.setUserId(userId);
        log.setChangeScore(delta);
        log.setReason(reason);
        log.setOrderId(orderId);
        creditLogMapper.insert(log);
    }

    private ProductOrder requireOrder(Long orderId) {
        ProductOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        }
        return order;
    }

    private OrderStatus parseStatus(String raw) {
        try {
            return OrderStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("订单状态数据异常：" + raw);
        }
    }
}
