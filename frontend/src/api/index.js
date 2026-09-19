import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 10000 })

// 封道审单、校准批次都要分身份。身份放在 localStorage，每个请求带头上。
export function identity() {
  try {
    return JSON.parse(localStorage.getItem('closure-identity') || 'null') || {}
  } catch {
    return {}
  }
}

export function saveIdentity(id) {
  // 合并写：封道页只动 role/tenant，校准页只动 role/hallId，互不冲掉
  localStorage.setItem('closure-identity', JSON.stringify(Object.assign(identity(), id)))
}

http.interceptors.request.use((cfg) => {
  const id = identity()
  cfg.headers['X-Role'] =
    id.role === '库房' ? 'warehouse' : id.role === '场馆值班' ? 'duty' : 'tenant'
  if (id.role === '承租方' && id.tenant) {
    cfg.headers['X-Tenant'] = encodeURIComponent(id.tenant)
  }
  if (id.hallId) {
    cfg.headers['X-Hall-Id'] = id.hallId
  }
  return cfg
})

http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    const msg = err?.response?.data?.message || err.message || '请求失败'
    return Promise.reject(new Error(msg))
  }
)

export const hallApi = {
  list: (params) => http.get('/halls', { params }),
  create: (data) => http.post('/halls', data),
  update: (id, data) => http.put(`/halls/${id}`, data)
}

export const boothApi = {
  list: (params) => http.get('/booths', { params }),
  create: (data) => http.post('/booths', data),
  update: (id, data) => http.put(`/booths/${id}`, data)
}

export const bookingApi = {
  list: (params) => http.get('/bookings', { params }),
  create: (data) => http.post('/bookings', data),
  update: (id, data) => http.put(`/bookings/${id}`, data)
}

export const closureApi = {
  list: (params) => http.get('/closures', { params }),
  submit: (data) => http.post('/closures', data),
  approve: (id) => http.post(`/closures/${id}/approve`),
  reject: (id, reason) =>
    http.post(`/closures/${id}/reject`, null, { params: { reason } })
}

export const equipmentApi = {
  listEquipments: (params) => http.get('/equipments', { params }),
  createEquipment: (data) => http.post('/equipments', data),
  updateEquipment: (id, data) => http.put(`/equipments/${id}`, data),
  listLoans: (params) => http.get('/loans', { params }),
  lend: (data) => http.post('/loans', data),
  giveBack: (id, backDate) =>
    http.post(`/loans/${id}/giveback`, null, { params: { backDate } })
}

export const calibApi = {
  list: (params) => http.get('/calibrations', { params }),
  detail: (id) => http.get(`/calibrations/${id}`),
  candidates: (params) => http.get('/calibrations/candidates', { params }),
  create: (data) => http.post('/calibrations', data),
  returnBatch: (id) => http.post(`/calibrations/${id}/return`)
}

export default http
