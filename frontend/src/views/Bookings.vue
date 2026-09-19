<template>
  <div class="grid-page">
    <div class="grid-tools">
      <el-select v-model="filters.status" placeholder="全部状态" clearable size="small" style="width: 130px">
        <el-option label="待布展" value="待布展" />
        <el-option label="展出中" value="展出中" />
        <el-option label="已结束" value="已结束" />
      </el-select>
      <el-input
        v-model="filters.keyword"
        placeholder="展会名或承租方"
        clearable
        size="small"
        style="width: 200px"
      />
      <el-button size="small" type="primary" @click="openNew">排一个新档期</el-button>
      <span class="grid-count">共 {{ rows.length }} 条排期</span>
    </div>

    <el-table :data="rows" border size="small" class="grid-table">
      <el-table-column label="展会信息" align="center">
        <el-table-column label="展会名称" min-width="160">
          <template #default="{ row }">
            <span class="expo-name">{{ row.expoName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="承租方" width="130" prop="tenant" />
      </el-table-column>

      <el-table-column label="展位信息" align="center">
        <el-table-column label="展位号" width="96">
          <template #default="{ row }">
            <span class="mono">{{ boothCode(row.boothId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="展馆" width="110">
          <template #default="{ row }">{{ hallName(row.boothId) }}</template>
        </el-table-column>
        <el-table-column label="面积㎡" width="86" align="right">
          <template #default="{ row }">{{ boothArea(row.boothId) }}</template>
        </el-table-column>
      </el-table-column>

      <el-table-column label="档期" align="center">
        <el-table-column label="开展" width="112" prop="startDate" />
        <el-table-column label="结束" width="112" prop="endDate" />
        <el-table-column label="天数" width="72" align="right">
          <template #default="{ row }">{{ days(row) }}</template>
        </el-table-column>
      </el-table-column>

      <el-table-column label="状态与操作" align="center" width="216">
        <el-table-column label="状态" width="96">
          <template #default="{ row }">
            <span class="bst" :class="statusClass(row.status)">{{ row.status }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <template v-if="row.status !== '已结束'">
              <a class="act" @click="openEdit(row)">改档期</a>
              <a class="act end" @click="finish(row)">结束</a>
            </template>
            <span v-else class="closed">已归档</span>
          </template>
        </el-table-column>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dlg" :title="form.id ? '改档期' : '排一个新档期'" width="500px">
      <el-form label-width="96px">
        <el-form-item label="排期单号">
          <el-input v-model="form.code" :disabled="!!form.id" placeholder="如 BK-0904" />
        </el-form-item>
        <el-form-item label="展位">
          <el-select v-model="form.boothId" style="width: 100%" :disabled="!!form.id">
            <el-option
              v-for="b in booths"
              :key="b.id"
              :label="`${b.code}（${b.kind} ${b.area}㎡ ${b.status}）`"
              :value="b.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="展会名称">
          <el-input v-model="form.expoName" />
        </el-form-item>
        <el-form-item label="承租方">
          <el-input v-model="form.tenant" />
        </el-form-item>
        <el-form-item label="开展日期">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="结束日期">
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="form.id" label="排期状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="待布展" value="待布展" />
            <el-option label="展出中" value="展出中" />
            <el-option label="已结束" value="已结束" />
          </el-select>
          <div class="status-hint">改成「展出中」或「已结束」后，这条排期名下待审、已批准的封道单会自动作废</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { bookingApi, boothApi, hallApi } from '../api'

const bookings = ref([])
const booths = ref([])
const halls = ref([])
const dlg = ref(false)
const form = ref({})
const filters = ref({ status: '', keyword: '' })

const rows = computed(() =>
  bookings.value.filter((b) => {
    const f = filters.value
    if (f.status && b.status !== f.status) return false
    if (f.keyword && !b.expoName.includes(f.keyword) && !b.tenant.includes(f.keyword)) return false
    return true
  })
)

function statusClass(s) {
  if (s === '待布展') return 's-wait'
  if (s === '展出中') return 's-live'
  return 's-done'
}

function boothOf(id) {
  return booths.value.find((b) => b.id === id)
}

function boothCode(id) {
  const b = boothOf(id)
  return b ? b.code : id
}

function boothArea(id) {
  const b = boothOf(id)
  return b ? b.area : '-'
}

function hallName(boothId) {
  const b = boothOf(boothId)
  if (!b) return '-'
  const h = halls.value.find((x) => x.id === b.hallId)
  return h ? h.name : '-'
}

function days(row) {
  const a = new Date(row.startDate + 'T00:00:00')
  const b = new Date(row.endDate + 'T00:00:00')
  return Math.round((b - a) / 86400000) + 1
}

async function load() {
  try {
    bookings.value = await bookingApi.list({})
    booths.value = await boothApi.list({})
    halls.value = await hallApi.list({})
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openNew() {
  form.value = {}
  dlg.value = true
}

function openEdit(row) {
  form.value = { ...row }
  dlg.value = true
}

async function save() {
  try {
    if (form.value.id) {
      await bookingApi.update(form.value.id, form.value)
    } else {
      await bookingApi.create(form.value)
    }
    ElMessage.success('已保存')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function finish(row) {
  try {
    await bookingApi.update(row.id, { status: '已结束' })
    ElMessage.success(`「${row.expoName}」已结束`)
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
.grid-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}
.grid-count {
  margin-left: auto;
  font-size: 13px;
  color: #8b93a7;
}
.expo-name {
  font-weight: 500;
}
.mono {
  font-family: Menlo, monospace;
  font-size: 12px;
}
.bst {
  font-size: 11px;
  border-radius: 4px;
  padding: 2px 8px;
}
.bst.s-wait {
  background: #fdf6ec;
  color: #b88230;
}
.bst.s-live {
  background: #fdeef3;
  color: #b83a66;
}
.bst.s-done {
  background: #f4f4f5;
  color: #909399;
}
.act {
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
  margin-right: 10px;
}
.act.end {
  color: #6b7280;
}
.closed {
  font-size: 12px;
  color: #c0c4cc;
}
.status-hint {
  font-size: 12px;
  color: #b83a66;
  line-height: 1.4;
  margin-top: 2px;
}
</style>
