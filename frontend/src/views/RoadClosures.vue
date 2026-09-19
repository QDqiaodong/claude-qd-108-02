<template>
  <div class="grid-page">
    <!-- 身份条：承租方申报 / 场馆值班审单，承租方自己没有批准按钮 -->
    <div class="role-bar">
      <div class="role-switch">
        <span class="role-label">当前身份</span>
        <el-radio-group v-model="me.role" size="small" @change="onRoleChange">
          <el-radio-button value="承租方">承租方</el-radio-button>
          <el-radio-button value="场馆值班">场馆值班（审单）</el-radio-button>
        </el-radio-group>
        <el-select
          v-if="me.role === '承租方'"
          v-model="me.tenant"
          size="small"
          placeholder="选承租方"
          style="width: 180px"
          @change="persistMe"
        >
          <el-option v-for="t in tenants" :key="t" :label="t" :value="t" />
        </el-select>
        <span v-if="me.role === '承租方' && !me.tenant" class="role-warn">
          先选你是哪家承租方，才能报封道
        </span>
        <span v-else-if="me.role === '场馆值班'" class="role-ok">
          值班可批准 / 驳回待审单
        </span>
      </div>
      <div class="fire-tip">
        消防口径：同一展馆同一天，同一条卸货通道在交叉时段只能封一段（哪怕一东一西两头也不许并行），
        后交的单带先占家的展会名与起止钟点驳回
      </div>
    </div>

    <div class="grid-tools">
      <el-select v-model="filters.status" placeholder="全部状态" clearable size="small" style="width: 120px">
        <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-model="filters.hallId" placeholder="全部展馆" clearable size="small" style="width: 140px">
        <el-option v-for="h in halls" :key="h.id" :label="h.name" :value="h.id" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        placeholder="单号 / 展会 / 承租方 / 通道"
        clearable
        size="small"
        style="width: 220px"
      />
      <el-button
        v-if="me.role === '承租方'"
        size="small"
        type="primary"
        :disabled="!me.tenant"
        @click="openNew"
      >申报封道</el-button>
      <span class="grid-count">共 {{ rows.length }} 张封道单</span>
    </div>

    <el-table :data="rows" border size="small" class="grid-table">
      <el-table-column label="单号" width="104">
        <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
      </el-table-column>
      <el-table-column label="先占展会（承租方）" min-width="190">
        <template #default="{ row }">
          <div class="expo-name">{{ row.expoName }}</div>
          <div class="sub">{{ row.tenant }}</div>
        </template>
      </el-table-column>
      <el-table-column label="展馆" width="100">
        <template #default="{ row }">{{ hallName(row.hallId) }}</template>
      </el-table-column>
      <el-table-column label="展位号" width="96">
        <!-- 条上印的号：改号后待审 / 已批准的条子已一起换新，门卫按这个号放行 -->
        <template #default="{ row }"><span class="mono">{{ row.boothCode }}</span></template>
      </el-table-column>
      <el-table-column label="封道日期" width="112" prop="closeDate" />
      <el-table-column label="卸货通道·段" min-width="180">
        <template #default="{ row }">
          <div>{{ row.channel }}</div>
          <div class="sub">{{ row.segment }}</div>
        </template>
      </el-table-column>
      <el-table-column label="封道钟点" width="118">
        <template #default="{ row }">{{ row.startTime.slice(0, 5) }}-{{ row.endTime.slice(0, 5) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="92">
        <template #default="{ row }">
          <span class="cst" :class="statusClass(row.status)">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="原因 / 审单" min-width="210">
        <template #default="{ row }">
          <div v-if="row.reason" class="reason">原因：{{ row.reason }}</div>
          <div v-else-if="row.status === '已批准'" class="sub">值班已于 {{ fmt(row.reviewedAt) }} 批准</div>
          <div v-else-if="row.status === '待审'" class="sub">提交于 {{ fmt(row.submittedAt) }}，等值班审</div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="132" fixed="right">
        <template #default="{ row }">
          <template v-if="me.role === '场馆值班' && row.status === '待审'">
            <a class="act ok" @click="doApprove(row)">批准</a>
            <a class="act no" @click="openReject(row)">驳回</a>
          </template>
          <span v-else-if="row.status === '待审'" class="closed">等值班审单</span>
          <span v-else class="closed">已闭环</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 承租方申报：排期只能挑自己那条仍停在待布展的 -->
    <el-dialog v-model="dlg" title="布展日卸货通道封道申报" width="520px">
      <el-form label-width="108px">
        <el-form-item label="挂哪条排期">
          <el-select v-model="form.bookingId" style="width: 100%" placeholder="只能选自己待布展的排期" @change="onPickBooking">
            <el-option
              v-for="b in myPendingBookings"
              :key="b.id"
              :label="`${b.code} ${b.expoName}（${b.startDate}~${b.endDate}）`"
              :value="b.id"
            />
          </el-select>
          <div v-if="myPendingBookings.length === 0" class="form-hint warn">
            你名下没有「待布展」的排期，封道单挂不上排期
          </div>
        </el-form-item>
        <el-form-item label="展馆">
          <el-input :model-value="pickedHallName" disabled placeholder="按排期自动带出" />
          <div v-if="pickedHallDisabled" class="form-hint warn">
            这间展馆已停用，停用馆新单批不出去
          </div>
        </el-form-item>
        <el-form-item label="封道日期">
          <el-date-picker v-model="form.closeDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="卸货通道">
          <el-select
            v-model="form.channel"
            style="width: 100%"
            placeholder="如 北卸货通道"
            filterable
            allow-create
            default-first-option
          >
            <el-option v-for="c in channelSuggestions" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="封哪一段">
          <el-select
            v-model="form.segment"
            style="width: 100%"
            placeholder="如 东段（3号门段）"
            filterable
            allow-create
            default-first-option
          >
            <el-option v-for="s in segmentSuggestions" :key="s" :label="s" :value="s" />
          </el-select>
          <div class="form-hint">消防不看段：同一通道交叉时段，封两头也不许并行</div>
        </el-form-item>
        <el-form-item label="开封时间">
          <el-time-select
            v-model="form.startTime"
            :max-time="form.endTime || '23:30'"
            start="06:00"
            step="00:30"
            end="22:00"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="解封时间">
          <el-time-select
            v-model="form.endTime"
            :min-time="form.startTime || '06:00'"
            start="06:30"
            step="00:30"
            end="23:30"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交申报</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rejectDlg" title="驳回封道单" width="420px">
      <el-input
        v-model="rejectReason"
        type="textarea"
        :rows="3"
        placeholder="写明驳回原因，会留在单上给承租方看"
      />
      <template #footer>
        <el-button @click="rejectDlg = false">取消</el-button>
        <el-button type="danger" @click="doReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { bookingApi, boothApi, hallApi, closureApi, identity, saveIdentity } from '../api'

const statuses = ['待审', '已批准', '已驳回', '作废']
const closures = ref([])
const bookings = ref([])
const booths = ref([])
const halls = ref([])
const dlg = ref(false)
const rejectDlg = ref(false)
const rejectReason = ref('')
const rejectTarget = ref(null)
const submitting = ref(false)
const form = ref({})
const filters = ref({ status: '', hallId: null, keyword: '' })

const me = ref(Object.assign({ role: '承租方', tenant: '' }, identity()))

const tenants = computed(() => {
  const set = new Set()
  bookings.value.forEach((b) => b.status === '待布展' && set.add(b.tenant))
  return [...set]
})

const rows = computed(() =>
  closures.value.filter((c) => {
    const f = filters.value
    if (f.status && c.status !== f.status) return false
    if (f.hallId && c.hallId !== f.hallId) return false
    if (f.keyword) {
      const k = f.keyword
      if (![c.code, c.expoName, c.tenant, c.channel].some((x) => x && x.includes(k))) return false
    }
    return true
  })
)

/** 承租方只能挑自己那条仍停在待布展的排期 */
const myPendingBookings = computed(() =>
  bookings.value.filter((b) => b.status === '待布展' && b.tenant === me.value.tenant)
)

const pickedBooking = computed(() =>
  bookings.value.find((b) => b.id === form.value.bookingId) || null
)

const pickedHall = computed(() => {
  const b = pickedBooking.value
  if (!b) return null
  const booth = booths.value.find((x) => x.id === b.boothId)
  return booth ? halls.value.find((h) => h.id === booth.hallId) || null : null
})

const pickedHallName = computed(() => (pickedHall.value ? pickedHall.value.name : ''))
const pickedHallDisabled = computed(() => pickedHall.value?.status === '停用')

const channelSuggestions = computed(() => {
  const b = pickedBooking.value
  if (!b) return []
  const hallId = pickedHall.value?.id
  return [...new Set(
    closures.value.filter((c) => c.hallId === hallId).map((c) => c.channel)
  )]
})

const segmentSuggestions = ['东段（1号门段）', '中段（2号门段）', '西段（3号门段）', '北段（4号门段）', '南段（5号门段）']

function statusClass(s) {
  if (s === '待审') return 'c-wait'
  if (s === '已批准') return 'c-ok'
  if (s === '已驳回') return 'c-no'
  return 'c-void'
}

function hallName(id) {
  const h = halls.value.find((x) => x.id === id)
  return h ? h.name : id
}

function fmt(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

function persistMe() {
  saveIdentity({ role: me.value.role, tenant: me.value.tenant })
}

function onRoleChange() {
  persistMe()
}

async function load() {
  try {
    const [cs, bs, hs, bks] = await Promise.all([
      closureApi.list({}),
      boothApi.list({}),
      hallApi.list({}),
      bookingApi.list({})
    ])
    closures.value = cs
    booths.value = bs
    halls.value = hs
    bookings.value = bks
    if (me.value.role === '承租方' && !me.value.tenant && tenants.value.length) {
      me.value.tenant = tenants.value[0]
      persistMe()
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openNew() {
  form.value = {
    bookingId: myPendingBookings.value[0]?.id || null,
    closeDate: '',
    channel: '',
    segment: '',
    startTime: '',
    endTime: ''
  }
  dlg.value = true
}

function onPickBooking() {
  form.value.channel = ''
  form.value.segment = ''
}

async function submit() {
  const f = form.value
  if (!f.bookingId) return ElMessage.error('要选一条自己待布展的排期')
  if (!f.closeDate) return ElMessage.error('要写清哪一天封道')
  if (!f.channel) return ElMessage.error('要写清哪条通道')
  if (!f.segment) return ElMessage.error('要写清封哪一段')
  if (!f.startTime || !f.endTime) return ElMessage.error('要写清从几点封到几点')
  submitting.value = true
  try {
    const saved = await closureApi.submit(f)
    ElMessage.success(`封道单 ${saved.code} 已提交，等场馆值班审`)
    dlg.value = false
    await load()
  } catch (e) {
    // 撞单时后端带出先占着的那家展会名和起止钟点，整条提示摆给承租方看
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function doApprove(row) {
  try {
    await ElMessageBox.confirm(
      `批准 ${row.code}（${row.expoName} ${row.closeDate} ${row.startTime.slice(0, 5)}-${row.endTime.slice(0, 5)}）？`,
      '值班批准',
      { type: 'warning', confirmButtonText: '批准', cancelButtonText: '再看看' }
    )
  } catch {
    return
  }
  try {
    await closureApi.approve(row.id)
    ElMessage.success(`${row.code} 已批准`)
    await load()
  } catch (e) {
    // 抢单落败时带上先到的单号；排期已改状态 / 展馆停用也在这里带原因
    ElMessage.error(e.message)
  }
}

function openReject(row) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectDlg.value = true
}

async function doReject() {
  if (!rejectReason.value.trim()) {
    return ElMessage.error('驳回要写明原因')
  }
  try {
    await closureApi.reject(rejectTarget.value.id, rejectReason.value.trim())
    ElMessage.success(`${rejectTarget.value.code} 已驳回`)
    rejectDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.grid-page {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 16px 18px 20px;
}
.role-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 14px;
  padding: 10px 14px;
  background: #fcedf2;
  border: 1px solid #f8c8d9;
  border-radius: 8px;
}
.role-switch {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.role-label {
  font-size: 13px;
  color: #6b7280;
}
.role-warn {
  font-size: 12px;
  color: #b83a66;
}
.role-ok {
  font-size: 12px;
  color: #2f7d4f;
}
.fire-tip {
  font-size: 12px;
  color: #9a5b74;
  max-width: 520px;
  line-height: 1.5;
}
.grid-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}
.grid-count {
  margin-left: auto;
  font-size: 13px;
  color: #8b93a7;
}
.mono {
  font-family: Menlo, monospace;
  font-size: 12px;
}
.expo-name {
  font-weight: 500;
}
.sub {
  font-size: 12px;
  color: #98a0b5;
}
.reason {
  font-size: 12px;
  color: #b83a66;
  line-height: 1.5;
}
.form-hint {
  font-size: 12px;
  color: #98a0b5;
  line-height: 1.4;
}
.form-hint.warn {
  color: #b83a66;
}
.cst {
  font-size: 11px;
  border-radius: 4px;
  padding: 2px 8px;
  white-space: nowrap;
}
.cst.c-wait {
  background: #fdf6ec;
  color: #b88230;
}
.cst.c-ok {
  background: #eafaf0;
  color: #2f7d4f;
}
.cst.c-no {
  background: #fef0f0;
  color: #c45656;
}
.cst.c-void {
  background: #f4f4f5;
  color: #909399;
}
.act {
  font-size: 13px;
  cursor: pointer;
  margin-right: 10px;
}
.act.ok {
  color: #2f7d4f;
}
.act.no {
  color: #c45656;
}
.closed {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
