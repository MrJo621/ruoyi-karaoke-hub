<template>
  <div class="app-container karaoke-statistics">
    <div class="dashboard-title">
      <div>
        <div class="title-main">KTV 数据驾驶舱</div>
        <div class="title-sub">实时在线、点歌热度、歌曲与歌手排行榜</div>
      </div>
      <div class="title-pulse">LIVE</div>
    </div>

    <el-row :gutter="16" class="stat-row">
      <el-col v-for="(item, index) in cards" :key="item.key" :xs="12" :sm="8" :md="4">
        <div class="stat-card" :style="{ '--accent': item.color, '--delay': index * 0.08 + 's' }">
          <div class="stat-label">{{ item.label }}</div>
          <div class="stat-value">{{ overview[item.key] || 0 }}</div>
          <div class="stat-line"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="panel-header">
            <span>点歌曲线</span>
            <el-radio-group v-model="trendDays" size="mini" @change="loadTrend">
              <el-radio-button :label="7">7天</el-radio-button>
              <el-radio-button :label="14">14天</el-radio-button>
              <el-radio-button :label="30">30天</el-radio-button>
            </el-radio-group>
          </div>
          <div ref="trendChart" class="chart main-chart"></div>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="panel-header">
            <span>客户端在线情况</span>
            <el-button type="text" icon="el-icon-refresh" @click="loadClients">刷新</el-button>
          </div>
          <el-table class="neon-table" :data="clients" height="320" size="small">
            <el-table-column label="设备编号" prop="deviceId" :show-overflow-tooltip="true" />
            <el-table-column label="状态" width="80" align="center">
              <template slot-scope="scope">
                <el-tag :type="scope.row.online ? 'success' : 'info'" size="mini">
                  {{ scope.row.online ? '在线' : '离线' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近活跃" prop="lastSeen" width="150">
              <template slot-scope="scope">
                {{ parseTime(scope.row.lastSeen) }}
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16">
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="panel-header">
            <span>热门歌曲排行榜</span>
            <el-button type="text" icon="el-icon-refresh" @click="loadRanks">刷新</el-button>
          </div>
          <el-table class="neon-table" :data="songRank" height="360" size="small">
            <el-table-column label="#" type="index" width="48" align="center" />
            <el-table-column label="歌曲" prop="songTitle" :show-overflow-tooltip="true" />
            <el-table-column label="歌手" prop="singerName" :show-overflow-tooltip="true" />
            <el-table-column label="点歌次数" prop="playCount" width="100" align="center" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" class="panel-card">
          <div slot="header" class="panel-header">
            <span>热门歌手排行榜</span>
            <el-button type="text" icon="el-icon-refresh" @click="loadRanks">刷新</el-button>
          </div>
          <div ref="singerChart" class="chart rank-chart"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script>
import * as echarts from 'echarts'
import { getClients, getOverview, getSingerRank, getSongRank, getTrend } from '@/api/karaoke/statistics'

export default {
  name: 'KaraokeStatistics',
  data() {
    return {
      loading: false,
      trendDays: 14,
      overview: {},
      clients: [],
      songRank: [],
      singerRank: [],
      trendChart: null,
      singerChart: null,
      timer: null,
      cards: [
        { key: 'onlineCount', label: '当前在线', color: '#21e6ff' },
        { key: 'clientCount', label: '累计客户端', color: '#8b5cf6' },
        { key: 'totalPlayCount', label: '累计点歌', color: '#ff2d6f' },
        { key: 'songCount', label: '歌曲总数', color: '#facc15' },
        { key: 'readySongCount', label: '可点歌曲', color: '#34d399' },
        { key: 'singerCount', label: '歌手总数', color: '#fb7185' }
      ]
    }
  },
  mounted() {
    this.initCharts()
    this.loadAll()
    window.addEventListener('resize', this.resizeCharts)
    this.timer = setInterval(this.loadRealtime, 15000)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.resizeCharts)
    if (this.timer) clearInterval(this.timer)
    if (this.trendChart) this.trendChart.dispose()
    if (this.singerChart) this.singerChart.dispose()
  },
  methods: {
    initCharts() {
      this.trendChart = echarts.init(this.$refs.trendChart)
      this.singerChart = echarts.init(this.$refs.singerChart)
    },
    loadAll() {
      this.loading = true
      Promise.all([this.loadOverview(), this.loadClients(), this.loadTrend(), this.loadRanks()])
        .finally(() => { this.loading = false })
    },
    loadRealtime() {
      this.loadOverview()
      this.loadClients()
    },
    loadOverview() {
      return getOverview().then(res => {
        this.overview = res.data || {}
      })
    },
    loadClients() {
      return getClients().then(res => {
        this.clients = res.data || []
      })
    },
    loadRanks() {
      return Promise.all([
        getSongRank(10).then(res => { this.songRank = res.data || [] }),
        getSingerRank(10).then(res => {
          this.singerRank = res.data || []
          this.renderSingerChart()
        })
      ])
    },
    loadTrend() {
      return getTrend(this.trendDays).then(res => {
        const rows = res.data || []
        this.renderTrendChart(rows)
      })
    },
    renderTrendChart(rows) {
      const days = rows.map(item => item.day)
      const values = rows.map(item => item.playCount || 0)
      this.trendChart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          backgroundColor: 'rgba(11,16,32,.92)',
          borderColor: '#21e6ff',
          textStyle: { color: '#dff7ff' },
          axisPointer: { type: 'line', lineStyle: { color: '#ff2d6f', width: 2 } }
        },
        grid: { top: 34, left: 46, right: 22, bottom: 40 },
        xAxis: {
          type: 'category',
          boundaryGap: false,
          data: days,
          axisLine: { lineStyle: { color: 'rgba(170,219,255,.45)' } },
          axisLabel: { color: '#a8c7e8' },
          axisTick: { show: false }
        },
        yAxis: {
          type: 'value',
          minInterval: 1,
          splitLine: { lineStyle: { color: 'rgba(33,230,255,.12)' } },
          axisLabel: { color: '#a8c7e8' }
        },
        series: [{
          name: '点歌次数',
          type: 'line',
          smooth: true,
          symbol: 'circle',
          symbolSize: 8,
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(255,45,111,.42)' },
              { offset: 0.48, color: 'rgba(33,230,255,.16)' },
              { offset: 1, color: 'rgba(33,230,255,0)' }
            ])
          },
          lineStyle: {
            width: 4,
            shadowBlur: 16,
            shadowColor: 'rgba(255,45,111,.85)',
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#21e6ff' },
              { offset: 0.45, color: '#8b5cf6' },
              { offset: 1, color: '#ff2d6f' }
            ])
          },
          itemStyle: { color: '#fff', borderColor: '#ff2d6f', borderWidth: 3 },
          data: values
        }]
      })
    },
    renderSingerChart() {
      const names = this.singerRank.map(item => item.singerName).reverse()
      const values = this.singerRank.map(item => item.playCount || 0).reverse()
      this.singerChart.setOption({
        backgroundColor: 'transparent',
        tooltip: {
          trigger: 'axis',
          axisPointer: { type: 'shadow' },
          backgroundColor: 'rgba(11,16,32,.92)',
          borderColor: '#ff2d6f',
          textStyle: { color: '#fff' }
        },
        grid: { top: 24, left: 82, right: 26, bottom: 30 },
        xAxis: {
          type: 'value',
          minInterval: 1,
          splitLine: { lineStyle: { color: 'rgba(33,230,255,.12)' } },
          axisLabel: { color: '#a8c7e8' }
        },
        yAxis: {
          type: 'category',
          data: names,
          axisLine: { lineStyle: { color: 'rgba(170,219,255,.4)' } },
          axisLabel: { color: '#dff7ff' },
          axisTick: { show: false }
        },
        series: [{
          name: '点歌次数',
          type: 'bar',
          barWidth: 16,
          itemStyle: {
            borderRadius: [0, 10, 10, 0],
            shadowBlur: 14,
            shadowColor: 'rgba(33,230,255,.6)',
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#21e6ff' },
              { offset: 0.55, color: '#8b5cf6' },
              { offset: 1, color: '#ff2d6f' }
            ])
          },
          label: { show: true, position: 'right', color: '#dff7ff' },
          data: values
        }]
      })
    },
    resizeCharts() {
      if (this.trendChart) this.trendChart.resize()
      if (this.singerChart) this.singerChart.resize()
    }
  }
}
</script>

<style scoped>
.karaoke-statistics {
  min-height: calc(100vh - 84px);
  background:
    radial-gradient(circle at 12% 10%, rgba(255,45,111,.26), transparent 28%),
    radial-gradient(circle at 88% 6%, rgba(33,230,255,.22), transparent 26%),
    linear-gradient(135deg, #090e1e 0%, #15102a 44%, #090b16 100%);
  color: #dff7ff;
  position: relative;
  overflow: hidden;
}

.karaoke-statistics:before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
    linear-gradient(rgba(33,230,255,.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,45,111,.05) 1px, transparent 1px);
  background-size: 42px 42px;
  mask-image: linear-gradient(to bottom, rgba(0,0,0,.72), transparent);
}

.dashboard-title {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18px;
  padding: 18px 20px;
  border: 1px solid rgba(33,230,255,.24);
  border-radius: 8px;
  background: rgba(9, 14, 30, .72);
  box-shadow: 0 0 34px rgba(33,230,255,.12) inset, 0 18px 42px rgba(0,0,0,.22);
}

.title-main {
  font-size: 24px;
  font-weight: 800;
  color: #fff;
  text-shadow: 0 0 18px rgba(33,230,255,.8), 0 0 28px rgba(255,45,111,.55);
}

.title-sub {
  margin-top: 6px;
  color: #8fb8dc;
  font-size: 13px;
}

.title-pulse {
  padding: 8px 14px;
  border: 1px solid rgba(255,45,111,.55);
  border-radius: 999px;
  color: #fff;
  font-weight: 800;
  letter-spacing: 1px;
  background: rgba(255,45,111,.16);
  box-shadow: 0 0 20px rgba(255,45,111,.55);
  animation: pulseGlow 1.8s ease-in-out infinite;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  height: 92px;
  padding: 16px;
  margin-bottom: 16px;
  border-radius: 8px;
  position: relative;
  overflow: hidden;
  background: linear-gradient(145deg, rgba(17, 24, 49, .96), rgba(29, 16, 48, .88));
  border: 1px solid rgba(33,230,255,.22);
  box-shadow: 0 0 20px rgba(33,230,255,.12), inset 0 0 22px rgba(255,255,255,.04);
  animation: floatIn .45s ease both;
  animation-delay: var(--delay);
}

.stat-card:before {
  content: "";
  position: absolute;
  width: 92px;
  height: 92px;
  right: -26px;
  top: -28px;
  border-radius: 50%;
  background: var(--accent);
  opacity: .16;
  filter: blur(2px);
}

.stat-label {
  color: #9fc7e9;
  font-size: 13px;
}

.stat-value {
  margin-top: 12px;
  color: #fff;
  font-size: 28px;
  font-weight: 700;
  text-shadow: 0 0 16px var(--accent);
}

.stat-line {
  position: absolute;
  left: 16px;
  right: 16px;
  bottom: 12px;
  height: 3px;
  border-radius: 999px;
  background: linear-gradient(90deg, var(--accent), transparent);
  box-shadow: 0 0 12px var(--accent);
}

.panel-card {
  margin-bottom: 16px;
  border: 1px solid rgba(33,230,255,.24);
  background: rgba(11, 16, 32, .76);
  box-shadow: 0 18px 42px rgba(0,0,0,.24), inset 0 0 26px rgba(33,230,255,.05);
}

.panel-card ::v-deep .el-card__header {
  border-bottom: 1px solid rgba(33,230,255,.18);
  color: #fff;
}

.panel-card ::v-deep .el-card__body {
  background: transparent;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: #fff;
  font-weight: 700;
}

.panel-header span {
  text-shadow: 0 0 14px rgba(33,230,255,.75);
}

.panel-header ::v-deep .el-button {
  color: #21e6ff;
}

.panel-header ::v-deep .el-radio-button__inner {
  color: #9fc7e9;
  background: rgba(255,255,255,.04);
  border-color: rgba(33,230,255,.22);
}

.panel-header ::v-deep .el-radio-button__orig-radio:checked + .el-radio-button__inner {
  color: #fff;
  background: linear-gradient(90deg, #21e6ff, #ff2d6f);
  border-color: transparent;
  box-shadow: 0 0 18px rgba(255,45,111,.45);
}

.chart {
  height: 320px;
}

.main-chart {
  height: 338px;
}

.rank-chart {
  height: 360px;
}

.neon-table ::v-deep .el-table,
.neon-table ::v-deep .el-table__header-wrapper,
.neon-table ::v-deep .el-table__body-wrapper,
.neon-table ::v-deep .el-table__fixed,
.neon-table ::v-deep .el-table__fixed-right,
.neon-table ::v-deep .el-table__expanded-cell,
.neon-table ::v-deep .el-table__empty-block {
  background: transparent !important;
}

.neon-table {
  border: 1px solid rgba(33,230,255,.16);
  border-radius: 6px;
  overflow: hidden;
  background:
    radial-gradient(circle at 20% 20%, rgba(33,230,255,.09), transparent 32%),
    radial-gradient(circle at 82% 90%, rgba(255,45,111,.08), transparent 34%),
    rgba(7, 12, 28, .62);
}

.neon-table ::v-deep th,
.neon-table ::v-deep tr,
.neon-table ::v-deep td {
  background: transparent !important;
  color: #dff7ff;
  border-bottom-color: rgba(33,230,255,.12);
}

.neon-table ::v-deep .el-table__body {
  background: linear-gradient(180deg, rgba(33,230,255,.045), rgba(255,45,111,.035));
}

.neon-table ::v-deep th {
  color: #8feaff;
  background: rgba(33,230,255,.08) !important;
  font-weight: 700;
}

.neon-table ::v-deep .el-table__empty-block {
  min-height: 220px;
}

.neon-table ::v-deep .el-table__empty-text {
  color: #8feaff;
  padding: 10px 18px;
  border: 1px solid rgba(33,230,255,.26);
  border-radius: 999px;
  background: rgba(33,230,255,.08);
  box-shadow: 0 0 20px rgba(33,230,255,.16);
}

.neon-table ::v-deep .el-table__row:hover > td {
  background: rgba(33,230,255,.08) !important;
}

.neon-table ::v-deep .el-table:before {
  background: rgba(33,230,255,.18);
}

@keyframes pulseGlow {
  0%, 100% { opacity: .72; box-shadow: 0 0 14px rgba(255,45,111,.35); }
  50% { opacity: 1; box-shadow: 0 0 26px rgba(255,45,111,.85); }
}

@keyframes floatIn {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
