<template>
  <div class="search-page" :class="{ collapsed }">
    <aside class="side" :class="{ collapsed }">
      <div class="side-top">
        <span v-show="!collapsed" class="side-title">找展位</span>
        <span class="fold" @click="collapsed = !collapsed">{{ collapsed ? '»' : '«' }}</span>
      </div>

      <div v-show="!collapsed" class="side-body">
        <el-input
          v-model="filters.keyword"
          placeholder="展位号，如 A-101"
          clearable
          size="large"
          @keyup.enter="apply"
        >
          <template #prefix><span class="mag">🔍</span></template>
        </el-input>

        <div class="fgroup">
          <div class="flabel">所在展馆</div>
          <div class="pills">
            <span class="pill" :class="{ on: filters.hallId === null }" @click="setHall(null)">全部</span>
            <span
              v-for="h in halls"
              :key="h.id"
              class="pill"
              :class="{ on: filters.hallId === h.id }"
              @click="setHall(h.id)"
            >
              {{ h.name }}
            </span>
          </div>
        </div>

        <div class="fgroup">
          <div class="flabel">展位状态</div>
          <div class="pills">
            <span class="pill" :class="{ on: filters.status === '' }" @click="filters.status = ''">全部</span>
            <span
              v-for="s in ['空闲', '已租', '维修']"
              :key="s"
              class="pill"
              :class="{ on: filters.status === s }"
              @click="filters.status = s"
            >
              {{ s }}
            </span>
          </div>
        </div>

        <div class="fgroup">
          <div class="flabel">展位类型</div>
          <div class="pills">
            <span class="pill" :class="{ on: filters.kind === '' }" @click="filters.kind = ''">全部</span>
            <span
              v-for="k in ['标准', '特装', '光地']"
              :key="k"
              class="pill"
              :class="{ on: filters.kind === k }"
              @click="filters.kind = k"
            >
              {{ k }}
            </span>
          </div>
        </div>

        <div class="fgroup">
          <div class="flabel">面积不小于 {{ filters.minArea }} ㎡</div>
          <el-slider v-model="filters.minArea" :min="0" :max="600" :step="50" />
        </div>

        <div class="side-actions">
          <el-button type="primary" @click="apply">开始找</el-button>
          <el-button @click="reset">清空</el-button>
        </div>
        <el-button link type="primary" class="add-link" @click="openNew">＋ 摆一个新展位</el-button>
      </div>
    </aside>

    <section class="result">
      <div class="result-head">
        <span class="rh-count">找到 <b>{{ rows.length }}</b> 个展位</span>
        <span class="rh-sum">
          面积合计 {{ rows.reduce((s, b) => s + b.area, 0) }} ㎡
        </span>
        <span class="rh-hint">点名片直接改</span>
      </div>

      <div class="card-wrap">
        <div v-for="b in rows" :key="b.id" class="bcard">
          <div class="bcard-top">
            <span class="bcode">{{ b.code }}</span>
            <span class="bst" :class="statusClass(b.status)">{{ b.status }}</span>
          </div>
          <div class="bmeta">
            <span>{{ hallName(b.hallId) }}</span>
            <span class="dot">·</span>
            <span>{{ b.kind }}</span>
          </div>
          <div class="barea">{{ b.area }} <small>㎡</small></div>

          <div class="bedit">
            <el-select v-model="forms[b.id].kind" size="small" style="width: 86px" @change="save(b)">
              <el-option label="标准" value="标准" />
              <el-option label="特装" value="特装" />
              <el-option label="光地" value="光地" />
            </el-select>
            <el-select
              v-model="forms[b.id].status"
              size="small"
              style="width: 86px"
              :disabled="b.status === '已租'"
              @change="save(b)"
            >
              <el-option label="空闲" value="空闲" />
              <el-option label="维修" value="维修" />
            </el-select>
          </div>
        </div>
        <div v-if="!rows.length" class="no-result">没有符合条件的展位，把条件放宽点试试</div>
      </div>
    </section>

    <el-dialog v-model="dlg" title="摆一个新展位" width="470px">
      <el-form label-width="88px">
        <el-form-item label="展位编号">
          <el-input v-model="newBooth.code" placeholder="如 A-104" />
        </el-form-item>
        <el-form-item label="所在展馆">
          <el-select v-model="newBooth.hallId" style="width: 100%">
            <el-option
              v-for="h in halls"
              :key="h.id"
              :label="`${h.code} ${h.name}（${h.status}，剩 ${h.area - usedArea(h.id)} ㎡）`"
              :value="h.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="面积">
          <el-input-number v-model="newBooth.area" :min="1" :step="10" />
          <span class="unit">㎡</span>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="newBooth.kind" style="width: 100%">
            <el-option label="标准" value="标准" />
            <el-option label="特装" value="特装" />
            <el-option label="光地" value="光地" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="create">摆上</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { boothApi, hallApi } from '../api'

const halls = ref([])
const all = ref([])
const collapsed = ref(false)
const dlg = ref(false)
const newBooth = ref({})
const forms = reactive({})

const filters = ref({ keyword: '', hallId: null, status: '', kind: '', minArea: 0 })

const rows = computed(() =>
  all.value.filter((b) => {
    const f = filters.value
    if (f.keyword && !b.code.includes(f.keyword)) return false
    if (f.hallId !== null && b.hallId !== f.hallId) return false
    if (f.status && b.status !== f.status) return false
    if (f.kind && b.kind !== f.kind) return false
    if (b.area < f.minArea) return false
    return true
  })
)

function statusClass(s) {
  if (s === '空闲') return 's-free'
  if (s === '已租') return 's-rented'
  return 's-fix'
}

function hallName(id) {
  const hit = halls.value.find((h) => h.id === id)
  return hit ? hit.name : id
}

function usedArea(hallId) {
  return all.value.filter((b) => b.hallId === hallId).reduce((s, b) => s + b.area, 0)
}

function setHall(id) {
  filters.value.hallId = id
}

function reset() {
  filters.value = { keyword: '', hallId: null, status: '', kind: '', minArea: 0 }
}

function apply() {
  // 条件都是响应式的，点一下只是为了有反馈
  ElMessage.success(`找到 ${rows.value.length} 个展位`)
}

function openNew() {
  newBooth.value = { kind: '标准', area: 100, hallId: halls.value.length ? halls.value[0].id : null }
  dlg.value = true
}

async function create() {
  try {
    await boothApi.create(newBooth.value)
    ElMessage.success('已摆上')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function save(b) {
  try {
    await boothApi.update(b.id, { kind: forms[b.id].kind, status: forms[b.id].status })
    ElMessage.success(`${b.code} 已保存`)
    await load()
  } catch (e) {
    ElMessage.error(e.message)
    await load()
  }
}

async function load() {
  try {
    halls.value = await hallApi.list({})
    all.value = await boothApi.list({})
    all.value.forEach((b) => {
      forms[b.id] = { kind: b.kind, status: b.status === '已租' ? '空闲' : b.status }
    })
  } catch (e) {
    ElMessage.error(e.message)
  }
}

watch(collapsed, () => {})

onMounted(load)
</script>

<style scoped>
.search-page {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.side {
  width: 292px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 14px 16px 18px;
  transition: width 0.18s;
  flex-shrink: 0;
}
.side.collapsed {
  width: 46px;
  padding: 14px 8px;
}
.side-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.side-title {
  font-size: 15px;
  font-weight: 600;
}
.fold {
  cursor: pointer;
  color: var(--el-color-primary);
  font-size: 15px;
  padding: 0 4px;
}
.side-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.mag {
  font-size: 13px;
}
.fgroup {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.flabel {
  font-size: 12px;
  color: #8b93a7;
}
.pills {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}
.pill {
  font-size: 12px;
  padding: 4px 11px;
  border-radius: 13px;
  background: #f4f6fb;
  color: #5b6478;
  cursor: pointer;
}
.pill:hover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}
.pill.on {
  background: var(--el-color-primary);
  color: #fff;
}
.side-actions {
  display: flex;
  gap: 8px;
}
.add-link {
  align-self: flex-start;
}
.result {
  flex: 1;
}
.result-head {
  display: flex;
  align-items: baseline;
  gap: 16px;
  margin-bottom: 12px;
}
.rh-count b {
  color: var(--el-color-primary);
  font-size: 17px;
}
.rh-sum {
  font-size: 13px;
  color: #6b7280;
}
.rh-hint {
  margin-left: auto;
  font-size: 12px;
  color: #b0b6c6;
}
.card-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.bcard {
  width: 216px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 13px 15px 14px;
}
.bcard-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.bcode {
  font-family: Menlo, monospace;
  font-size: 13px;
  font-weight: 600;
}
.bst {
  font-size: 11px;
  border-radius: 4px;
  padding: 1px 7px;
}
.bst.s-free {
  background: #f0f9eb;
  color: #529b2e;
}
.bst.s-rented {
  background: #fdeef3;
  color: #b83a66;
}
.bst.s-fix {
  background: #f4f4f5;
  color: #909399;
}
.bmeta {
  font-size: 12px;
  color: #8b93a7;
  margin: 5px 0 2px;
}
.dot {
  margin: 0 5px;
}
.barea {
  font-size: 22px;
  font-weight: 600;
  color: #3a4152;
  margin-bottom: 10px;
}
.barea small {
  font-size: 12px;
  font-weight: 400;
  color: #8b93a7;
}
.bedit {
  display: flex;
  gap: 8px;
}
.no-result {
  color: #b0b6c6;
  font-size: 13px;
  padding: 46px 0;
  width: 100%;
  text-align: center;
}
.unit {
  margin-left: 8px;
  color: #8b93a7;
  font-size: 13px;
}
</style>
