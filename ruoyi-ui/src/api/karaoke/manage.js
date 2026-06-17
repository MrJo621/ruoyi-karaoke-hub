import request from '@/utils/request'


// 查询MV列表
export function listMV(query) {
    return request({
        url: '/karaoke/manage/list/mv',
        method: 'get',
        params: query
    })
}

// 提交并解析新的MV
export function uploadMV(data) {
    return request({
        url: '/karaoke/manage/upload/mv',
        method: 'post',
        data: data
    })
}

// 批量导入MV，导入后不自动解析
export function uploadMVBatch(data) {
    return request({
        url: '/karaoke/manage/upload/mv/batch',
        method: 'post',
        data: data
    })
}

// 选定解析 / 重新解析MV
export function parseMV(ids) {
    return request({
        url: '/karaoke/manage/parse/mv',
        method: 'post',
        data: ids
    })
}

// 更新MV
export function updateMV(data) {
    return request({
        url: '/karaoke/manage/update/mv',
        method: 'post',
        data: data
    })
}

// 删除MV
export function delMV(id) {
    return request({
        url: '/karaoke/manage/mv/' + id,
        method: 'delete'
    })
}

// 查询歌手列表
export function listSinger(query) {
    return request({
        url: '/karaoke/manage/list/singer',
        method: 'get',
        params: query
    })
}

// 新增歌手
export function addSinger(data) {
    return request({
        url: '/karaoke/manage/singer',
        method: 'post',
        data: data
    })
}

// 修改歌手
export function updateSinger(data) {
    return request({
        url: '/karaoke/manage/singer',
        method: 'put',
        data: data
    })
}

// 删除歌手
export function delSinger(id) {
    return request({
        url: '/karaoke/manage/singer/' + id,
        method: 'delete'
    })
}
