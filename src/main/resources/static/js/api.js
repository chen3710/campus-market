/* ==========================================================================
   接口封装：统一携带 JWT、统一处理业务错误码
   ========================================================================== */

const TOKEN_KEY = 'cm_token';
const USER_KEY = 'cm_user';

const Auth = {
    token() {
        return localStorage.getItem(TOKEN_KEY);
    },
    user() {
        try {
            return JSON.parse(localStorage.getItem(USER_KEY) || 'null');
        } catch (e) {
            return null;
        }
    },
    save(payload) {
        localStorage.setItem(TOKEN_KEY, payload.token);
        localStorage.setItem(USER_KEY, JSON.stringify(payload.user));
    },
    updateUser(user) {
        localStorage.setItem(USER_KEY, JSON.stringify(user));
    },
    clear() {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_KEY);
    },
    isLogin() {
        return !!localStorage.getItem(TOKEN_KEY);
    }
};

/**
 * 统一请求方法。
 * 后端约定 HTTP 状态码恒为 200，业务结果由响应体的 code 字段表达，
 * 所以这里只需要判断 code === 200。
 */
async function request(url, options = {}) {
    const { method = 'GET', body, form, silent = false } = options;

    const headers = {};
    const token = Auth.token();
    if (token) headers['Authorization'] = 'Bearer ' + token;

    let payload;
    if (form) {
        payload = form;
    } else if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
        payload = JSON.stringify(body);
    }

    let response;
    try {
        response = await fetch(url, { method, headers, body: payload });
    } catch (e) {
        if (!silent) toast('网络异常，请确认后端服务已启动', 'error');
        throw e;
    }

    let envelope;
    try {
        envelope = await response.json();
    } catch (e) {
        if (!silent) toast('服务返回格式异常（HTTP ' + response.status + '）', 'error');
        throw new Error('invalid response');
    }

    if (envelope.code === 401) {
        Auth.clear();
        if (!silent) toast(envelope.message || '登录已失效，请重新登录', 'error');
        const back = location.pathname.split('/').pop() + location.search;
        setTimeout(() => { location.href = 'login.html?redirect=' + encodeURIComponent(back); }, 900);
        throw new Error(envelope.message);
    }

    if (envelope.code !== 200) {
        if (!silent) toast(envelope.message || '操作失败', 'error');
        throw new Error(envelope.message || '操作失败');
    }

    return envelope;
}

const api = {
    get(url, options) {
        return request(url, options);
    },
    post(url, body) {
        return request(url, { method: 'POST', body });
    },
    put(url, body) {
        return request(url, { method: 'PUT', body });
    },
    del(url) {
        return request(url, { method: 'DELETE' });
    },
    upload(file) {
        const form = new FormData();
        form.append('file', file);
        return request('/api/files/upload', { method: 'POST', form });
    }
};

/* ------------------------------ 业务接口 ------------------------------ */

const ProductApi = {
    page(params) {
        return api.get('/api/products?' + new URLSearchParams(params).toString());
    },
    mine(params) {
        return api.get('/api/products/mine?' + new URLSearchParams(params).toString());
    },
    detail(id) {
        return api.get('/api/products/' + id);
    },
    create(data) {
        return api.post('/api/products', data);
    },
    update(id, data) {
        return api.put('/api/products/' + id, data);
    },
    remove(id) {
        return api.del('/api/products/' + id);
    },
    offShelf(id) {
        return api.post('/api/products/' + id + '/off-shelf');
    },
    onShelf(id) {
        return api.post('/api/products/' + id + '/on-shelf');
    }
};

const OrderApi = {
    create(data) {
        return api.post('/api/orders', data);
    },
    bought(page, size) {
        return api.get('/api/orders/bought?page=' + page + '&size=' + size);
    },
    sold(page, size) {
        return api.get('/api/orders/sold?page=' + page + '&size=' + size);
    },
    confirm(id) {
        return api.post('/api/orders/' + id + '/confirm');
    },
    finish(id) {
        return api.post('/api/orders/' + id + '/finish');
    },
    cancel(id, reason) {
        return api.post('/api/orders/' + id + '/cancel', { reason: reason || '' });
    }
};

const CommentApi = {
    list(productId) {
        return api.get('/api/products/' + productId + '/comments');
    },
    create(productId, content) {
        return api.post('/api/products/' + productId + '/comments', { content, parentId: 0 });
    }
};

const FavoriteApi = {
    toggle(productId) {
        return api.post('/api/favorites/' + productId);
    },
    mine(page, size) {
        return api.get('/api/favorites/mine?page=' + page + '&size=' + size);
    }
};

const UserApi = {
    me() {
        return api.get('/api/auth/me');
    },
    updateProfile(data) {
        return api.put('/api/users/me', data);
    },
    creditLogs() {
        return api.get('/api/users/me/credit-logs');
    }
};

const CategoryApi = {
    list() {
        return api.get('/api/categories');
    }
};

const AdminApi = {
    users(page, size, keyword) {
        return api.get('/api/admin/users?page=' + page + '&size=' + size
            + '&keyword=' + encodeURIComponent(keyword || ''));
    },
    updateStatus(id, status) {
        return api.put('/api/admin/users/' + id + '/status?status=' + status);
    }
};
