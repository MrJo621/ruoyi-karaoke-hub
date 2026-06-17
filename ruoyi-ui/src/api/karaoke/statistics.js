import request from '@/utils/request'

export function getOverview() {
  return request({
    url: '/karaoke/statistics/overview',
    method: 'get'
  })
}

export function getClients() {
  return request({
    url: '/karaoke/statistics/clients',
    method: 'get'
  })
}

export function getSongRank(limit) {
  return request({
    url: '/karaoke/statistics/song/rank',
    method: 'get',
    params: { limit }
  })
}

export function getSingerRank(limit) {
  return request({
    url: '/karaoke/statistics/singer/rank',
    method: 'get',
    params: { limit }
  })
}

export function getTrend(days) {
  return request({
    url: '/karaoke/statistics/trend',
    method: 'get',
    params: { days }
  })
}
