/* ==========================================================================
   通用工具 + 顶部导航渲染
   ========================================================================== */

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

/* ------------------------------ 基础工具 ------------------------------ */

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value).replace(/[&<>"']/g, ch => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[ch]));
}

function toast(message, type = '') {
    let wrap = $('.toast-wrap');
    if (!wrap) {
        wrap = document.createElement('div');
        wrap.className = 'toast-wrap';
        document.body.appendChild(wrap);
    }
    const el = document.createElement('div');
    el.className = 'toast' + (type ? ' ' + type : '');
    el.textContent = message;
    wrap.appendChild(el);
    setTimeout(() => {
        el.style.transition = 'opacity .2s';
        el.style.opacity = '0';
        setTimeout(() => el.remove(), 220);
    }, 2200);
}

function formatMoney(value) {
    if (value === null || value === undefined || value === '') return '0.00';
    return Number(value).toFixed(2);
}

function firstChar(text) {
    if (!text) return '?';
    const trimmed = String(text).trim();
    return trimmed ? trimmed.charAt(0) : '?';
}

function formatTime(value) {
    if (!value) return '';
    return String(value).replace('T', ' ').slice(0, 16);
}

/** 按 id 生成稳定的色相，保证同一件商品每次刷新颜色一致 */
function coverStyle(seed) {
    const hue = (Number(seed || 1) * 47) % 360;
    return 'background: linear-gradient(135deg, hsl(' + hue + ', 55%, 62%), hsl('
        + ((hue + 38) % 360) + ', 52%, 47%));';
}

function coverHtml(product, tagHtml = '') {
    if (product.cover) {
        return '<div class="cover"><img src="' + escapeHtml(product.cover) + '" alt="'
            + escapeHtml(product.title) + '" loading="lazy">' + tagHtml + '</div>';
    }
    return '<div class="cover" style="' + coverStyle(product.id) + '">'
        + '<span class="cover-placeholder">' + escapeHtml(firstChar(product.title)) + '</span>'
        + tagHtml + '</div>';
}

/* ------------------------------ 状态字典 ------------------------------ */

const PRODUCT_STATUS = {
    ON_SALE: { text: '在售', cls: 'badge-green' },
    LOCKED: { text: '已被下单', cls: 'badge-amber' },
    SOLD: { text: '已售出', cls: 'badge-gray' },
    OFF_SHELF: { text: '已下架', cls: 'badge-gray' }
};

const ORDER_STATUS = {
    CREATED: { text: '待卖家确认', cls: 'badge-amber' },
    CONFIRMED: { text: '待面交', cls: 'badge-blue' },
    FINISHED: { text: '已完成', cls: 'badge-green' },
    CANCELLED: { text: '已取消', cls: 'badge-gray' }
};

function productStatusBadge(status) {
    const item = PRODUCT_STATUS[status] || { text: status, cls: 'badge-gray' };
    return '<span class="badge ' + item.cls + '">' + escapeHtml(item.text) + '</span>';
}

function orderStatusBadge(status) {
    const item = ORDER_STATUS[status] || { text: status, cls: 'badge-gray' };
    return '<span class="badge ' + item.cls + '">' + escapeHtml(item.text) + '</span>';
}

/* ------------------------------ 导航 ------------------------------ */

const NAV_ITEMS = [
    { key: 'index', href: 'index.html', label: '逛市场' },
    { key: 'publish', href: 'publish.html', label: '发布闲置', auth: true },
    { key: 'products', href: 'center.html?tab=products', label: '我的商品', auth: true },
    { key: 'orders', href: 'center.html?tab=bought', label: '我的订单', auth: true },
    { key: 'favorites', href: 'center.html?tab=favorites', label: '我的收藏', auth: true },
    { key: 'admin', href: 'admin.html', label: '后台管理', admin: true }
];

function renderNav(activeKey) {
    const user = Auth.user();

    const links = NAV_ITEMS
        .filter(item => (!item.auth || user) && (!item.admin || (user && user.role === 'ADMIN')))
        .map(item => '<a class="nav-link' + (item.key === activeKey ? ' active' : '')
            + '" href="' + item.href + '">' + item.label + '</a>')
        .join('');

    let right;
    if (user) {
        const avatar = user.avatar
            ? '<img src="' + escapeHtml(user.avatar) + '" alt="">'
            : escapeHtml(firstChar(user.nickname));
        right = '<div class="nav-user" onclick="location.href=\'center.html\'" title="个人中心">'
            + '<span class="avatar">' + avatar + '</span>'
            + '<span class="fs-13">' + escapeHtml(user.nickname) + '</span>'
            + '</div>'
            + '<button class="btn btn-sm" onclick="logout()">退出</button>';
    } else {
        right = '<a class="btn btn-sm" href="login.html">登录</a>'
            + '<a class="btn btn-sm btn-primary" href="login.html?mode=register">注册</a>';
    }

    document.body.insertAdjacentHTML('afterbegin',
        '<header class="nav"><div class="nav-inner">'
        + '<a class="brand" href="index.html"><span class="brand-mark">校</span>校园二手</a>'
        + '<nav class="nav-links">' + links + '</nav>'
        + '<div class="nav-right">' + right + '</div>'
        + '</div></header>');
}

function logout() {
    Auth.clear();
    toast('已退出登录', 'success');
    setTimeout(() => { location.href = 'index.html'; }, 350);
}

/** 未登录时跳转登录页，并记录来源以便登录后跳回 */
function requireLogin() {
    if (Auth.isLogin()) return true;
    const back = location.pathname.split('/').pop() + location.search;
    location.href = 'login.html?redirect=' + encodeURIComponent(back);
    return false;
}

/* ------------------------------ 分页组件 ------------------------------ */

function renderPagination(container, pageResult, onPageChange) {
    const el = typeof container === 'string' ? $(container) : container;
    if (!el) return;

    const totalPages = pageResult.pages || 0;
    const current = pageResult.page || 1;
    if (totalPages <= 1) {
        el.innerHTML = '';
        return;
    }

    const buttons = [];
    buttons.push('<button class="page-btn" data-page="' + (current - 1) + '"'
        + (current <= 1 ? ' disabled' : '') + '>上一页</button>');

    const start = Math.max(1, current - 2);
    const end = Math.min(totalPages, start + 4);
    for (let i = start; i <= end; i++) {
        buttons.push('<button class="page-btn' + (i === current ? ' active' : '')
            + '" data-page="' + i + '">' + i + '</button>');
    }

    buttons.push('<button class="page-btn" data-page="' + (current + 1) + '"'
        + (current >= totalPages ? ' disabled' : '') + '>下一页</button>');
    buttons.push('<span class="fs-13 text-3" style="margin-left:8px">共 ' + pageResult.total + ' 条</span>');

    el.innerHTML = buttons.join('');
    $$('.page-btn', el).forEach(btn => {
        btn.addEventListener('click', () => {
            const page = Number(btn.dataset.page);
            if (page >= 1 && page <= totalPages && page !== current) onPageChange(page);
        });
    });
}

/* ------------------------------ 空状态 ------------------------------ */

function emptyHtml(title, sub = '') {
    return '<div class="empty"><div class="empty-icon">空</div>'
        + '<div class="empty-title">' + escapeHtml(title) + '</div>'
        + (sub ? '<div class="fs-13">' + escapeHtml(sub) + '</div>' : '')
        + '</div>';
}

function loadingHtml(text = '加载中…') {
    return '<div class="empty"><div class="fs-13">' + escapeHtml(text) + '</div></div>';
}

/** 从地址栏读取查询参数 */
function queryParam(name, fallback = '') {
    return new URLSearchParams(location.search).get(name) || fallback;
}

/* ------------------------------ 模态框 ------------------------------ */

function closeModal() {
    const mask = $('.modal-mask');
    if (mask) mask.remove();
}

/**
 * 通用模态框。
 * onConfirm 可以返回 Promise：确认按钮会自动进入 loading 状态，
 * 抛异常则保持弹窗打开，让用户改完再提交。
 */
function openModal({ title, bodyHtml, confirmText = '确定', cancelText = '取消', onConfirm }) {
    closeModal();

    const html = '<div class="modal-mask">'
        + '<div class="modal">'
        + '<div class="modal-head">' + escapeHtml(title) + '</div>'
        + '<div class="modal-body">' + bodyHtml + '</div>'
        + '<div class="modal-foot">'
        + '<button class="btn" data-modal="cancel">' + escapeHtml(cancelText) + '</button>'
        + '<button class="btn btn-primary" data-modal="confirm">' + escapeHtml(confirmText) + '</button>'
        + '</div></div></div>';

    document.body.insertAdjacentHTML('beforeend', html);
    const mask = $('.modal-mask');

    $('[data-modal="cancel"]', mask).addEventListener('click', closeModal);
    mask.addEventListener('click', e => { if (e.target === mask) closeModal(); });

    $('[data-modal="confirm"]', mask).addEventListener('click', async e => {
        const btn = e.target;
        btn.disabled = true;
        const original = btn.textContent;
        btn.textContent = '处理中…';
        try {
            if (onConfirm) await onConfirm();
            closeModal();
        } catch (err) {
            /* 具体错误信息已由请求层 toast，这里只需恢复按钮 */
            btn.disabled = false;
            btn.textContent = original;
        }
    });

    return mask;
}

/** 二次确认弹窗，用于删除等不可逆操作 */
function confirmDialog(title, message, onConfirm, confirmText = '确认删除') {
    return openModal({
        title,
        bodyHtml: '<div style="line-height:1.75;color:var(--text-2)">' + escapeHtml(message) + '</div>',
        confirmText,
        onConfirm
    });
}

/* 按 ESC 关闭弹窗 */
document.addEventListener('keydown', e => {
    if (e.key === 'Escape') closeModal();
});
