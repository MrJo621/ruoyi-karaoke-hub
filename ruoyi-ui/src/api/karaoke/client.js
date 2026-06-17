import request from '@/utils/request'


// 查询MV列表
export function listMV(query) {
    return request({
        url: '/karaoke/client/mv/list',
        method: 'get',
        params: query
    })
}

export function listSong(query) {
  return request({
    url: '/karaoke/client/song/list',
    method: 'get',
    params: query
  })
}

export function addSong(query) {
    return request({
        url: '/karaoke/client/song/add',
        method: 'get',
        params: query
    })
}

export function removeSong(query) {
  return request({
    url: '/karaoke/client/song/remove',
    method: 'get',
    params: query
  })
}

export function orderSong(query) {
  return request({
    url: '/karaoke/client/song/order',
    method: 'get',
    params: query
  })
}
