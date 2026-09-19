<template>
  <div class="grid-page">
    <!-- 身份条：库房开批、标回厂；值班只能看本馆相关批次 -->
    <div class="role-bar">
      <div class="role-switch">
        <span class="role-label">当前身份</span>
        <el-radio-group v-model="me.role" size="small" @change="onRoleChange">
          <el-radio-button value="库房">库房</el-radio-button>
          <el-radio-button value="场馆值班">场馆值班</el-radio-button>
        </el-radio-group>
        <el-select
          v-if="me.role === '场馆值班'"
          v-model="me.hallId"
          size="small"
          placeholder="选本馆"
          style="width: 160px"
          @change="onHallChange"
        >
          <el-option v-for="h in halls" :key="h.id" :label="h.name" :value="h.id" />
        </el-select>
        <span v-if="me.role === '场馆值班' && !me.hallId" class="role-warn">
          先选本馆，只能看本馆相关的批次
        </span>
        <span v-else-if="me.role === '场馆值班'" class="role-ok">
          值班只能查看，回厂由库房操作
        </span>
        <span v-else class="role-ok">库房开批次、标已回厂</span>
      </div>
      <div class="cal-tip">
        校准占用的件数当场从可借里扣掉，新排期借不到这部分；借用行一件不动，租金照旧按借用件数算。
        排期结束后还在厂里的占用转「待归还厂」，可借不加回，等批次回厂才放。
      </div>
    </div>

    <div class="grid-tools">
      <el-select v-model="filters.status" placeholder="全部状态" clearable size="small" style="width: 120px">
        <el-option v-for="s in statuses" :key="s" :label="s" :value="s" />
      </el-select>
      <el-button
        v-if="me.role === '库房'"
        size="small"
        type="primary"
        @click="openNew"
      >开校准批次</el-button>
      <span class="grid-count">共 {{ rows.length }} 批</span>
    </div>

    <el-table
      v-if="me.role === '库房' || me.hallId"
      :data="rows"
      border
      size="small"
      class="grid-table"
      @expand-change="onExpand"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="lines-box">
            <div v-if="!details[row.id]" class="lines-loading">占用行加载中…</div>
            <table v-else class="lines-table">
              <thead>
                <tr>
                  <th>占用展会</th>
                  <th>展馆</th>
                  <th>借用行</th>
                  <th>占用件数</th>
                  <th>状态</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="ln in details[row.id].lines" :key="ln.id">
                  <td>{{ ln.expoName }}</td>
                  <td>{{ hallName(ln.hallId) }}</td>
                  <td class="mono">借用 #{{ ln.loanId }}</td>
                  <td>{{ ln.quantity }} 件</td>
                  <td><span class="cst" :class="occClass(ln.status)">{{ ln.status }}</span></td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="批次号" width="104">
        <template #default="{ row }"><span class="mono">{{ row.code }}</span></template>
      </el-table-column>
      <el-table-column label="类别" width="80" prop="kind" />
      <el-table-column label="件数" width="80">
        <template #default="{ row }">{{ row.quantity }} 件</template>
      </el-table-column>
      <el-table-column label="预计回厂日" width="112" prop="expectBackDate" />
      <el-table-column label="状态" width="92">
        <template #default="{ row }">
          <span class="cst" :class="row.status === '校准中' ? 'c-wait' : 'c-ok'">{{ row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="开批时间" width="150">
        <template #default="{ row }">{{ fmt(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="回厂时间" width="150">
        <template #default="{ row }">{{ row.backAt ? fmt(row.backAt) : '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <a
            v-if="me.role === '库房' && row.status === '校准中'"
            class="act ok"
            @click="doReturn(row)"
          >标已回厂</a>
          <span v-else-if="row.status === '校准中'" class="closed">等库房回厂</span>
          <span v-else class="closed">已闭环</span>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-else description="值班先选本馆，才能看本馆相关的校准批次" />

    <!-- 库房开批：选类别、件数、预计回厂日，点名占用哪几条借用行 -->
    <el-dialog v-model="dlg" title="开校准批次" width="780px">
      <el-form label-width="96px">
        <el-form-item label="类别">
          <el-select v-model="form.kind" style="width: 200px" @change="loadCandidates">
            <el-option v-for="k in kinds" :key="k" :label="k" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="预计回厂日">
          <el-date-picker
            v-model="form.expectBackDate"
            type="date"
            value-format="YYYY-MM-DD"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="点名占用">
          <div class="cand-box">
            <table v-if="candidates.length" class="lines-table cand">
              <thead>
                <tr>
                  <th>展会（排期状态）</th>
                  <th>展馆</th>
                  <th>展具</th>
                  <th>借出</th>
                  <th>已占</th>
                  <th>本次占用</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in candidates" :key="c.loanId" :class="{ off: !occupiable(c) }">
                  <td>
                    {{ c.expoName }}
                    <span class="cst c-wait">{{ c.bookingStatus }}</span>
                  </td>
                  <td>
                    {{ c.hallName }}
                    <span v-if="c.hallStatus === '停用'" class="cst c-no">已关停</span>
                  </td>
                  <td>{{ c.equipmentName }}</td>
                  <td>{{ c.quantity }} 件</td>
                  <td>{{ c.holding }} 件</td>
                  <td>
                    <el-input-number
                      v-model="picked[c.loanId]"
                      :min="0"
                      :max="c.remain"
                      size="small"
                      :disabled="!occupiable(c)"
                      style="width: 110px"
                    />
                  </td>
                </tr>
              </tbody>
            </table>
            <div v-else class="cand-empty">
              「{{ form.kind }}」没有可占用的借用行（须挂在待布展 / 展出中的排期上）
            </div>
          </div>
          <div class="form-hint" :class="{ warn: sumPicked !== form.quantity }">
            已点合计 {{ sumPicked }} 件 / 本批件数 {{ form.quantity }} 件，两者要对齐才能开批
          </div>
        </el-form-item>
        <el-form-item label="本批件数">
          <el-input-number v-model="form.quantity" :min="1" style="width: 160px" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">开批</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { calibApi, hallApi, identity, saveIdentity } from '../api'

const kinds = ['桁架', '展板', '桌椅', '灯具', '地毯']
const statuses = ['校准中', '已回厂']
const halls = ref([])
const batches = ref([])
const details = ref({})
const filters = ref({ status: '' })
const dlg = ref(false)
const submitting = ref(false)
const candidates = ref([])
const picked = ref({})
const form = ref({})

const saved = identity()
const me = ref({
  role: saved.role === '场馆值班' ? '场馆值班' : '库房',
  hallId: saved.hallId || null
})

const rows = computed(() =>
  batches.value.filter((b) => !filters.value.status || b.status === filters.value.status)
)

const sumPicked = computed(() =>
  Object.values(picked.value).reduce((a, b) => a + (b || 0), 0)
)

// 点占用行时把合计带进本批件数，仍可手改；对不上后端会挡
watch(sumPicked, (v) => {
  if (dlg.value && v > 0) form.value.quantity = v
})

function occupiable(c) {
  return c.remain > 0 && c.hallStatus !== '停用'
}

function hallName(id) {
  const h = halls.value.find((x) => x.id === id)
  return h ? h.name : id
}

function occClass(s) {
  if (s === '校准中') return 'c-wait'
  if (s === '待归还厂') return 'c-hold'
  return 'c-ok'
}

function fmt(t) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

function persistMe() {
  saveIdentity({ role: me.value.role, hallId: me.value.hallId })
}

function onRoleChange() {
  persistMe()
  load()
}

function onHallChange() {
  persistMe()
  load()
}

async function load() {
  // 值班没选本馆时不发请求，服务端也会挡：值班只能看本馆相关批次
  if (me.value.role === '场馆值班' && !me.value.hallId) {
    batches.value = []
    return
  }
  try {
    batches.value = await calibApi.list({})
    details.value = {}
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function onExpand(row, expanded) {
  if (!expanded.includes(row) || details.value[row.id]) return
  try {
    const d = await calibApi.detail(row.id)
    details.value = Object.assign({}, details.value, { [row.id]: d })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadCandidates() {
  picked.value = {}
  try {
    candidates.value = await calibApi.candidates({ kind: form.value.kind })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openNew() {
  form.value = {
    kind: '灯具',
    quantity: 1,
    expectBackDate: ''
  }
  dlg.value = true
  loadCandidates()
}

async function submit() {
  const f = form.value
  if (!f.kind) return ElMessage.error('要选类别')
  if (!f.expectBackDate) return ElMessage.error('要填预计回厂日')
  const lines = Object.entries(picked.value)
    .filter(([, q]) => q > 0)
    .map(([loanId, quantity]) => ({ loanId: Number(loanId), quantity }))
  if (!lines.length) return ElMessage.error('要点名占用哪几条借用行')
  if (sumPicked.value !== f.quantity) {
    return ElMessage.error(`已点合计 ${sumPicked.value} 件，跟本批件数 ${f.quantity} 对不上`)
  }
  submitting.value = true
  try {
    const savedBatch = await calibApi.create({
      kind: f.kind,
      quantity: f.quantity,
      expectBackDate: f.expectBackDate,
      lines
    })
    ElMessage.success(`批次 ${savedBatch.code} 已开，占用件数已从可借里扣掉`)
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

async function doReturn(row) {
  try {
    await ElMessageBox.confirm(
      `批次 ${row.code}（${row.kind} ${row.quantity} 件）标已回厂？占用行的件数会加回可借。`,
      '库房回厂',
      { type: 'warning', confirmButtonText: '标已回厂', cancelButtonText: '再等等' }
    )
  } catch {
    return
  }
  try {
    await calibApi.returnBatch(row.id)
    ElMessage.success(`${row.code} 已回厂，件数已加回可借`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  // 第一次进来身份可能是封道页留的「承租方」，先把本页默认身份落盘再发请求
  persistMe()
  try {
    halls.value = await hallApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
  await load()
})
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
.cal-tip {
  font-size: 12px;
  color: #9a5b74;
  max-width: 560px;
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
.lines-box {
  padding: 8px 18px 12px 48px;
}
.lines-loading {
  font-size: 12px;
  color: #b0b6c6;
  padding: 8px 0;
}
.lines-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.lines-table th,
.lines-table td {
  text-align: left;
  padding: 6px 10px;
  border-bottom: 1px solid #f4f5f9;
}
.lines-table th {
  color: #8b93a7;
  font-weight: 500;
  background: #fafbfe;
}
.lines-table.cand td {
  vertical-align: middle;
}
.lines-table tr.off td {
  color: #c0c4cc;
  background: #fafafa;
}
.cand-box {
  width: 100%;
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
}
.cand-empty {
  padding: 22px 0;
  text-align: center;
  color: #b0b6c6;
  font-size: 12px;
}
.form-hint {
  font-size: 12px;
  color: #98a0b5;
  line-height: 1.4;
  margin-top: 6px;
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
.cst.c-hold {
  background: #fdeef3;
  color: #b83a66;
}
.cst.c-ok {
  background: #eafaf0;
  color: #2f7d4f;
}
.cst.c-no {
  background: #fef0f0;
  color: #c45656;
}
.act {
  font-size: 13px;
  cursor: pointer;
  margin-right: 10px;
}
.act.ok {
  color: #2f7d4f;
}
.closed {
  font-size: 12px;
  color: #c0c4cc;
}
</style>
