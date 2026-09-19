<template>
  <div class="equip-page">
    <div class="eq-tools">
      <el-select v-model="kind" placeholder="全部类别" clearable size="small" style="width: 130px">
        <el-option v-for="k in kinds" :key="k" :label="k" :value="k" />
      </el-select>
      <el-input v-model="keyword" placeholder="编号或名称" clearable size="small" style="width: 180px" />
      <el-button size="small" type="primary" @click="openNew">登记新展具</el-button>
      <span class="eq-tip">点任意一类展具，下面弹出这一类的借用明细</span>
    </div>

    <div class="eq-list">
      <div
        v-for="e in rows"
        :key="e.id"
        class="eq-card"
        :class="{ picked: current && current.id === e.id, empty: e.available === 0 }"
        @click="open(e)"
      >
        <div class="eq-top">
          <span class="eq-code">{{ e.code }}</span>
          <span class="eq-kind">{{ e.kind }}</span>
        </div>
        <div class="eq-name">{{ e.name }}</div>
        <div class="eq-nums">
          <span class="big" :class="{ zero: e.available === 0 }">{{ e.available }}</span>
          <span class="slash">/ {{ e.total }}</span>
          <span class="unit">可借</span>
        </div>
        <div class="eq-bar">
          <div
            class="eq-fill"
            :style="{ width: (e.total ? (e.available / e.total) * 100 : 0) + '%' }"
          ></div>
        </div>
        <div v-if="e.calibrating > 0" class="eq-cal">校准占用 {{ e.calibrating }} 件在厂</div>
      </div>
    </div>

    <el-drawer
      v-model="drawer"
      direction="btt"
      size="46%"
      :with-header="false"
      class="eq-drawer"
    >
      <div v-if="current" class="dr">
        <div class="dr-head">
          <div>
            <span class="dr-code">{{ current.code }}</span>
            <span class="dr-name">{{ current.name }}</span>
            <span class="dr-kind">{{ current.kind }}</span>
          </div>
          <div class="dr-stat">
            可借 <b>{{ current.available }}</b> / 共 {{ current.total }}
            <span v-if="current.calibrating > 0" class="dr-cal">
              校准占用 {{ current.calibrating }} 件在厂
            </span>
          </div>
          <a class="dr-x" @click="drawer = false">收起 ▾</a>
        </div>

        <div class="dr-cols">
          <div class="dr-col">
            <div class="col-title">借给某个展会</div>
            <el-form label-width="84px" size="small">
              <el-form-item label="展会排期">
                <el-select v-model="lendForm.bookingId" style="width: 100%">
                  <el-option
                    v-for="b in bookable"
                    :key="b.id"
                    :label="`${b.expoName}（${b.tenant} ${b.startDate}~${b.endDate}）`"
                    :value="b.id"
                  />
                </el-select>
              </el-form-item>
              <el-form-item label="借用数量">
                <el-input-number v-model="lendForm.quantity" :min="1" :max="current.available" />
              </el-form-item>
              <el-form-item label="借出日期">
                <el-date-picker
                  v-model="lendForm.outDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="small" @click="doLend">借出</el-button>
                <el-button size="small" @click="openEditTotal">改总数</el-button>
              </el-form-item>
            </el-form>
          </div>

          <div class="dr-col wide">
            <div class="col-title">
              借用明细（借用中 {{ lending.length }} 条）
            </div>
            <div class="loan-list">
              <div v-for="l in loans" :key="l.id" class="loan-row">
                <span class="lr-exp" :class="{ back: l.status === '已归还' }">
                  {{ bookingLabel(l.bookingId) }}
                </span>
                <span class="lr-num">{{ l.quantity }} 件</span>
                <span class="lr-date">{{ l.outDate }} → {{ l.backDate || '未还' }}</span>
                <span class="lr-st" :class="l.status === '借用中' ? 'on' : 'off'">{{ l.status }}</span>
                <a v-if="l.status === '借用中'" class="lr-back" @click="doBack(l)">收回</a>
              </div>
              <div v-if="!loans.length" class="loan-empty">这类展具还没有借出记录</div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="dlg" title="登记新展具" width="470px">
      <el-form label-width="88px">
        <el-form-item label="编号">
          <el-input v-model="newForm.code" placeholder="如 EQ-005" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="newForm.name" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="newForm.kind" style="width: 100%">
            <el-option v-for="k in kinds" :key="k" :label="k" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="总数量">
          <el-input-number v-model="newForm.total" :min="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" @click="create">登记</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="totalDlg" title="改这类展具的总数" width="400px">
      <el-form label-width="88px">
        <el-form-item label="总数量">
          <el-input-number v-model="totalForm.total" :min="1" />
        </el-form-item>
      </el-form>
      <div class="total-tip">
        现在有 {{ totalForm.lending }} 件在外面借着<template v-if="totalForm.calibrating > 0">、
        {{ totalForm.calibrating }} 件在厂里校准</template>，总数不能改到比
        {{ totalForm.lending + (totalForm.calibrating || 0) }} 少。
      </div>
      <template #footer>
        <el-button @click="totalDlg = false">取消</el-button>
        <el-button type="primary" @click="saveTotal">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { equipmentApi, bookingApi } from '../api'

const kinds = ['桁架', '展板', '桌椅', '灯具', '地毯']
const equipments = ref([])
const bookings = ref([])
const loansAll = ref([])
const kind = ref('')
const keyword = ref('')
const drawer = ref(false)
const current = ref(null)
const dlg = ref(false)
const newForm = ref({})
const totalDlg = ref(false)
const totalForm = ref({})
const lendForm = ref({})

const rows = computed(() =>
  equipments.value.filter((e) => {
    if (kind.value && e.kind !== kind.value) return false
    if (keyword.value && !e.name.includes(keyword.value) && !e.code.includes(keyword.value)) return false
    return true
  })
)

const loans = computed(() =>
  current.value ? loansAll.value.filter((l) => l.equipmentId === current.value.id) : []
)

const lending = computed(() => loans.value.filter((l) => l.status === '借用中'))

const bookable = computed(() => bookings.value.filter((b) => b.status !== '已结束'))

function bookingLabel(id) {
  const hit = bookings.value.find((b) => b.id === id)
  return hit ? hit.expoName : id
}

async function load() {
  try {
    equipments.value = await equipmentApi.listEquipments({})
    bookings.value = await bookingApi.list({})
    loansAll.value = await equipmentApi.listLoans({})
    if (current.value) {
      const hit = equipments.value.find((e) => e.id === current.value.id)
      if (hit) current.value = hit
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function open(e) {
  current.value = e
  lendForm.value = {
    equipmentId: e.id,
    quantity: Math.min(10, Math.max(e.available, 1)),
    outDate: new Date().toISOString().slice(0, 10)
  }
  drawer.value = true
}

function openNew() {
  newForm.value = { kind: '桁架', total: 100 }
  dlg.value = true
}

async function create() {
  try {
    await equipmentApi.createEquipment(newForm.value)
    ElMessage.success('已登记')
    dlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doLend() {
  try {
    await equipmentApi.lend(lendForm.value)
    ElMessage.success('已借出')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function doBack(l) {
  try {
    await equipmentApi.giveBack(l.id, new Date().toISOString().slice(0, 10))
    ElMessage.success('已收回')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openEditTotal() {
  const cal = current.value.calibrating || 0
  totalForm.value = {
    id: current.value.id,
    total: current.value.total,
    lending: current.value.total - current.value.available - cal,
    calibrating: cal
  }
  totalDlg.value = true
}

async function saveTotal() {
  try {
    await equipmentApi.updateEquipment(totalForm.value.id, { total: totalForm.value.total })
    ElMessage.success('已保存')
    totalDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.equip-page {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 16px 18px 22px;
}
.eq-tools {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.eq-tip {
  margin-left: auto;
  font-size: 12px;
  color: #b0b6c6;
}
.eq-list {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}
.eq-card {
  width: 224px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 13px 15px 15px;
  cursor: pointer;
  transition: all 0.15s;
}
.eq-card:hover {
  border-color: var(--el-color-primary-light-5);
  transform: translateY(-2px);
}
.eq-card.picked {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px var(--el-color-primary-light-8);
}
.eq-card.empty {
  background: #fcfcfd;
}
.eq-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.eq-code {
  font-family: Menlo, monospace;
  font-size: 12px;
  color: var(--el-color-primary);
}
.eq-kind {
  font-size: 11px;
  background: #f4f6fb;
  color: #6b7280;
  border-radius: 4px;
  padding: 1px 7px;
}
.eq-name {
  font-size: 14px;
  font-weight: 500;
  margin: 6px 0 8px;
}
.eq-nums {
  display: flex;
  align-items: baseline;
  gap: 5px;
}
.big {
  font-size: 26px;
  font-weight: 600;
  color: var(--el-color-primary-dark-2);
  line-height: 1;
}
.big.zero {
  color: #c0c4cc;
}
.slash {
  font-size: 13px;
  color: #8b93a7;
}
.unit {
  font-size: 12px;
  color: #8b93a7;
}
.eq-bar {
  height: 6px;
  background: #f2f4f9;
  border-radius: 4px;
  margin-top: 10px;
  overflow: hidden;
}
.eq-fill {
  height: 100%;
  background: var(--el-color-primary-light-3);
  border-radius: 4px;
}
.eq-cal {
  margin-top: 8px;
  font-size: 11px;
  color: #b88230;
  background: #fdf6ec;
  border-radius: 4px;
  padding: 2px 7px;
  display: inline-block;
}
.dr {
  padding: 4px 10px 10px;
}
.dr-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-bottom: 12px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
}
.dr-code {
  font-family: Menlo, monospace;
  font-size: 13px;
  color: var(--el-color-primary);
  margin-right: 8px;
}
.dr-name {
  font-size: 15px;
  font-weight: 600;
  margin-right: 10px;
}
.dr-kind {
  font-size: 12px;
  background: #f4f6fb;
  color: #6b7280;
  border-radius: 4px;
  padding: 2px 8px;
}
.dr-stat {
  margin-left: 14px;
  font-size: 13px;
  color: #6b7280;
}
.dr-stat b {
  color: var(--el-color-primary-dark-2);
  font-size: 16px;
}
.dr-cal {
  margin-left: 10px;
  font-size: 11px;
  color: #b88230;
  background: #fdf6ec;
  border-radius: 4px;
  padding: 2px 7px;
}
.dr-x {
  margin-left: auto;
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
}
.dr-cols {
  display: flex;
  gap: 24px;
  margin-top: 14px;
}
.dr-col {
  width: 380px;
}
.dr-col.wide {
  flex: 1;
}
.col-title {
  font-size: 13px;
  font-weight: 600;
  color: #5b6478;
  margin-bottom: 10px;
}
.loan-list {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  max-height: 210px;
  overflow-y: auto;
}
.loan-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 9px 13px;
  font-size: 13px;
  border-bottom: 1px solid #f4f5f9;
}
.loan-row:last-child {
  border-bottom: none;
}
.lr-exp {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.lr-exp.back {
  color: #98a0b5;
}
.lr-num {
  color: var(--el-color-primary-dark-2);
  font-weight: 500;
}
.lr-date {
  font-size: 12px;
  color: #8b93a7;
}
.lr-st {
  font-size: 11px;
  border-radius: 4px;
  padding: 1px 7px;
}
.lr-st.on {
  background: #fdeef3;
  color: #b83a66;
}
.lr-st.off {
  background: #f4f4f5;
  color: #909399;
}
.lr-back {
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
}
.loan-empty {
  padding: 22px 0;
  text-align: center;
  color: #b0b6c6;
  font-size: 12px;
}
.total-tip {
  font-size: 12px;
  color: #8b93a7;
  padding: 0 0 4px 88px;
}
</style>
