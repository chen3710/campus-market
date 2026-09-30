package com.campus.market.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 接口层集成测试（JVM 内，不需要先启动应用）。
 * <p>
 * 补的是这样一块空白：纯单元测试碰不到拦截器、参数校验、全局异常处理这些
 * Spring MVC 的基础设施，而 shell 冒烟测试又要先起应用、跑得慢、还依赖外部进程。
 * 这一层用 MockMvc 在同一个 JVM 里把两者都覆盖掉，`./mvnw test` 就能跑完。
 * <p>
 * 跑在 h2 profile 上（内存库），所以不依赖本机 MySQL，CI 里也能跑。
 * 关掉定时任务，避免它在本类执行期间改动订单状态导致断言不稳定。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@TestPropertySource(properties = "app.order.enabled=false")
@DisplayName("接口层集成测试：鉴权与状态码语义")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private String body(MvcResult result) throws Exception {
        // 显式指定 UTF-8：响应里有中文提示，用平台默认编码会乱码
        return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(body(result));
    }

    /** 取统一响应体里的业务码 */
    private int code(MvcResult result) throws Exception {
        return json(result).get("code").asInt();
    }

    private MvcResult get(String url, String token) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.get(url);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(builder).andReturn();
    }

    private MvcResult post(String url, String token, String jsonBody) throws Exception {
        MockHttpServletRequestBuilder builder = MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (jsonBody != null) {
            builder.content(jsonBody);
        }
        return mvc.perform(builder).andReturn();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = post("/api/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
        JsonNode node = json(result).path("data").path("token");
        assertThat(node.isMissingNode() || node.isNull())
                .as("演示账号 %s 登录失败，请确认 DemoDataInitializer 正常执行", username)
                .isFalse();
        return node.asText();
    }

    // ------------------------------------------------------------------
    // 一、公开接口：不需要 Token
    // ------------------------------------------------------------------

    @Test
    @DisplayName("公开接口匿名可访问")
    void publicEndpointsAllowAnonymous() throws Exception {
        assertThat(code(get("/api/categories", null))).isEqualTo(200);
        assertThat(code(get("/api/products", null))).isEqualTo(200);
        assertThat(code(get("/api/products/1", null))).isEqualTo(200);
    }

    @Test
    @DisplayName("公开接口带无效 Token 时静默降级为匿名，而不是报 401")
    void publicEndpointsIgnoreInvalidToken() throws Exception {
        // 这是「可选登录」的关键行为：公开接口带 Token 只是为了显示收藏态，
        // Token 过期不该把匿名用户挡在门外
        assertThat(code(get("/api/products", "this-is-not-a-jwt"))).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // 二、鉴权：401 / 403
    // ------------------------------------------------------------------

    @Test
    @DisplayName("受保护接口未带 Token → 401")
    void protectedEndpointsRequireToken() throws Exception {
        assertThat(code(get("/api/orders/bought", null))).isEqualTo(401);
        assertThat(code(get("/api/orders/sold", null))).isEqualTo(401);
        assertThat(code(get("/api/favorites/mine", null))).isEqualTo(401);
        assertThat(code(get("/api/users/me/credit-logs", null))).isEqualTo(401);
        assertThat(code(post("/api/products", null, "{\"title\":\"x\",\"price\":10,\"categoryId\":1}")))
                .isEqualTo(401);
    }

    @Test
    @DisplayName("Token 被篡改 → 401")
    void tamperedTokenIsRejected() throws Exception {
        String token = login("alice", "123456");
        String tampered = token.substring(0, token.length() - 1) + "X";
        assertThat(code(get("/api/orders/bought", tampered))).isEqualTo(401);
    }

    @Test
    @DisplayName("格式完全不对的 Token → 401")
    void malformedTokenIsRejected() throws Exception {
        assertThat(code(get("/api/orders/bought", "abc.def.ghi"))).isEqualTo(401);
    }

    @Test
    @DisplayName("普通用户访问管理端 → 403（已登录但权限不足）")
    void nonAdminCannotAccessAdminEndpoints() throws Exception {
        String alice = login("alice", "123456");
        assertThat(code(get("/api/admin/users", alice))).isEqualTo(403);
    }

    @Test
    @DisplayName("管理员访问管理端 → 200")
    void adminCanAccessAdminEndpoints() throws Exception {
        String admin = login("admin", "123456");
        assertThat(code(get("/api/admin/users", admin))).isEqualTo(200);
    }

    // ------------------------------------------------------------------
    // 三、参数校验：400
    // ------------------------------------------------------------------

    @Test
    @DisplayName("参数校验失败 → 400")
    void validationFailureReturns400() throws Exception {
        String alice = login("alice", "123456");
        // 空标题
        assertThat(code(post("/api/products", alice, "{\"title\":\"\",\"price\":10,\"categoryId\":1}")))
                .isEqualTo(400);
        // 价格为 0
        assertThat(code(post("/api/products", alice, "{\"title\":\"测试\",\"price\":0,\"categoryId\":1}")))
                .isEqualTo(400);
    }

    @Test
    @DisplayName("请求体不是合法 JSON → 400 而不是 500")
    void malformedJsonReturns400() throws Exception {
        assertThat(code(post("/api/auth/login", null, "{not-json"))).isEqualTo(400);
    }

    @Test
    @DisplayName("缺少必填参数 → 400")
    void missingRequiredParamReturns400() throws Exception {
        assertThat(code(post("/api/auth/login", null, "{}"))).isEqualTo(400);
    }

    // ------------------------------------------------------------------
    // 四、资源与冲突：404 / 409
    // ------------------------------------------------------------------

    @Test
    @DisplayName("商品不存在 → 404")
    void missingProductReturns404() throws Exception {
        assertThat(code(get("/api/products/99999999", null))).isEqualTo(404);
    }

    @Test
    @DisplayName("用户名已被注册 → 409")
    void duplicateUsernameReturns409() throws Exception {
        MvcResult result = post("/api/auth/register", null,
                "{\"username\":\"alice\",\"password\":\"123456\",\"nickname\":\"重复注册\"}");
        assertThat(code(result)).isEqualTo(409);
    }

    // ------------------------------------------------------------------
    // 五、业务规则：422（区别于服务端出错的 999）
    // ------------------------------------------------------------------

    @Test
    @DisplayName("非法状态流转 → 422")
    void illegalStateTransitionReturns422() throws Exception {
        String alice = login("alice", "123456");
        String bob = login("bob", "123456");

        // alice 发布商品，bob 下单（订单进入 CREATED）
        int productId = json(post("/api/products", alice,
                "{\"title\":\"集成测试-状态流转\",\"price\":5.00,\"categoryId\":1}"))
                .get("data").asInt();
        int orderId = json(post("/api/orders", bob, "{\"productId\":" + productId + "}"))
                .get("data").get("id").asInt();

        // CREATED 不能直接跳到 FINISHED
        assertThat(code(post("/api/orders/" + orderId + "/finish", bob, null))).isEqualTo(422);
    }

    @Test
    @DisplayName("不能购买自己发布的商品 → 403")
    void cannotBuyOwnProduct() throws Exception {
        String alice = login("alice", "123456");
        int productId = json(post("/api/products", alice,
                "{\"title\":\"集成测试-自购\",\"price\":5.00,\"categoryId\":1}"))
                .get("data").asInt();
        assertThat(code(post("/api/orders", alice, "{\"productId\":" + productId + "}")))
                .isEqualTo(403);
    }

    @Test
    @DisplayName("封禁管理员账号 → 422")
    void cannotBanAdmin() throws Exception {
        String admin = login("admin", "123456");
        // 注意 status 是 @RequestParam 而不是请求体，写成 JSON body 会被当成缺少参数而返回 400
        long adminId = json(post("/api/auth/login", null,
                "{\"username\":\"admin\",\"password\":\"123456\"}"))
                .get("data").get("user").get("id").asLong();

        MvcResult result = mvc.perform(MockMvcRequestBuilders
                        .put("/api/admin/users/" + adminId + "/status")
                        .header("Authorization", "Bearer " + admin)
                        .param("status", "0"))
                .andReturn();
        assertThat(code(result)).isEqualTo(422);
    }

    @Test
    @DisplayName("封禁不存在的用户 → 404")
    void banningMissingUserReturns404() throws Exception {
        String admin = login("admin", "123456");
        MvcResult result = mvc.perform(MockMvcRequestBuilders
                        .put("/api/admin/users/999999/status")
                        .header("Authorization", "Bearer " + admin)
                        .param("status", "0"))
                .andReturn();
        assertThat(code(result)).isEqualTo(404);
    }

    // ------------------------------------------------------------------
    // 六、响应契约
    // ------------------------------------------------------------------

    @Test
    @DisplayName("无论成功失败，HTTP 状态码恒为 200，结果由 body 里的 code 表达")
    void httpStatusIsAlwaysOk() throws Exception {
        // 这条是项目刻意选择的契约：前端只需判断 code === 200，不用同时处理两层状态码。
        // 用 MockMvc 断言一遍，防止将来有人「顺手」给异常处理方法加上 @ResponseStatus 而破坏契约。
        mvc.perform(MockMvcRequestBuilders.get("/api/orders/bought"))
                .andExpect(status().isOk());
        mvc.perform(MockMvcRequestBuilders.get("/api/products/99999999"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("响应体结构统一：code / message / timestamp 三件套始终存在")
    void responseEnvelopeIsConsistent() throws Exception {
        for (MvcResult r : new MvcResult[]{
                get("/api/products", null),
                get("/api/orders/bought", null),
                get("/api/products/99999999", null)}) {
            JsonNode node = json(r);
            assertThat(node.hasNonNull("code")).as("缺少 code 字段").isTrue();
            assertThat(node.hasNonNull("message")).as("缺少 message 字段").isTrue();
            assertThat(node.hasNonNull("timestamp")).as("缺少 timestamp 字段").isTrue();
        }
    }

    @Test
    @DisplayName("响应体不泄露密码字段")
    void passwordIsNeverSerialized() throws Exception {
        String alice = login("alice", "123456");
        // 自己的资料、他人主页、管理端用户列表，三处都不能出现 password
        for (String url : new String[]{"/api/auth/me", "/api/users/2", "/api/admin/users"}) {
            String token = url.startsWith("/api/admin") ? login("admin", "123456") : alice;
            String raw = body(get(url, token));
            assertThat(raw).as("%s 的响应里出现了 password", url).doesNotContain("password");
        }
    }

    @Test
    @DisplayName("注册 → 登录 → 访问受保护接口：全链路打通")
    void registerLoginThenAccessProtected() throws Exception {
        String username = "it_user_" + System.currentTimeMillis() % 100000;
        MvcResult register = post("/api/auth/register", null,
                "{\"username\":\"" + username + "\",\"password\":\"123456\",\"nickname\":\"集成测试用户\"}");
        assertThat(code(register)).isEqualTo(200);
        // 注册后直接返回 Token，前端不用再登一次
        assertThat(json(register).path("data").path("token").asText()).isNotBlank();

        String token = login(username, "123456");
        assertThat(code(get("/api/auth/me", token))).isEqualTo(200);
        assertThat(code(get("/api/orders/bought", token))).isEqualTo(200);
    }

    @Test
    @DisplayName("密码错误与用户名不存在返回相同提示（防账号枚举）")
    void loginDoesNotLeakAccountExistence() throws Exception {
        String wrongPassword = json(post("/api/auth/login", null,
                "{\"username\":\"alice\",\"password\":\"wrong-password\"}")).get("message").asText();
        String noSuchUser = json(post("/api/auth/login", null,
                "{\"username\":\"nobody-here\",\"password\":\"wrong-password\"}")).get("message").asText();
        assertThat(wrongPassword).isEqualTo(noSuchUser);
    }
}
