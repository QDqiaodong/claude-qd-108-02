<template>
  <div class="plan-page">
    <div class="plan-tools">
      <span class="tools-title">展馆平面图</span>
      <div class="legend">
        <span><i class="sw free"></i>空闲</span>
        <span><i class="sw rented"></i>已租</span>
        <span><i class="sw fix"></i>维修</span>
      </div>
      <el-button type="primary" size="small" @click="openHall">新增展馆</el-button>
    </div>

    <div class="plan-wrap">
      <div v-for="h in halls" :key="h.id" class="hall-block" :class="{ closed: h.status !== '启用' }">
        <div class="hall-head">
          <span class="hall-code">{{ h.code }}</span>
          <span class="hall-name">{{ h.name }}</span>
          <span class="hall-area">{{ usedArea(h.id) }} / {{ h.area }} ㎡</span>
          <span class="hall-st" :class="statusClass(h.status)">{{ h.status }}</span>
        </div>
        <div class="hall-bar">
          <div class="hall-fill" :style="{ width: fill(h) + '%' }"></div>
        </div>
        <div class="booth-grid">
          <div
            v-for="b in boothsOf(h.id)"
            :key="b.id"
            class="booth-cell"
            :class="[b.status === '空闲' ? 'free' : b.status === '已租' ? 'rented' : 'fix',
                     { picked: pick && pick.id === b.id }]"
            :style="cellStyle(b)"
            @click="pick = b"
          >
            <div class="bc-code">{{ b.code }}</div>
            <div class="bc-area">{{ b.area }}㎡</div>
            <div class="bc-kind">{{ b.kind }}</div>
          </div>
          <div v-if="!boothsOf(h.id).length" class="grid-empty">这个展馆还没摆展位</div>
        </div>
      </div>
    </div>

    <div v-if="pick" class="float-card">
      <div class="fc-head">
        <span class="fc-code">{{ pick.code }}</span>
        <a class="fc-close" @click="pick = null">✕</a>
      </div>
      <div class="fc-row"><span>面积</span><b>{{ pick.area }} ㎡</b></div>
      <div class="fc-row"><span>类型</span><b>{{ pick.kind }}</b></div>
      <div class="fc-row"><span>展馆</span><b>{{ hallName(pick.hallId) }}</b></div>
      <div class="fc-row"><span>状态</span><b>{{ pick.status }}</b></div>
      <el-form label-width="56px" class="fc-form">
        <el-form-item label="类型">
          <el-select v-model="editForm.kind" size="small" style="width: 100%">
            <el-option label="标准" value="标准" />
            <el-option label="特装" value="特装" />
            <el-option label="光地" value="光地" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="editForm.status" size="small">
            <el-radio-button label="空闲" />
            <el-radio-button label="维修" />
          </el-radio-group>
        </el-form-item>
      </el-form>
      <el-button type="primary" size="small" style="width: 100%" @click="saveBooth">
        保存展位
      </el-button>
    </div>

    <el-dialog v-model="hallDlg" title="新增展馆" width="440px">
      <el-form label-width="88px">
        <el-form-item label="编号">
          <el-input v-model="newHall.code" placeholder="如 H-05" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="newHall.name" placeholder="如 五号展馆" />
        </el-form-item>
        <el-form-item label="可用面积">
          <el-input-number v-model="newHall.area" :min="1" :step="100" />
          <span class="unit">㎡</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="newHall.status" style="width: 100%">
            <el-option label="启用" value="启用" />
            <el-option label="布展" value="布展" />
            <el-option label="停用" value="停用" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hallDlg = false">取消</el-button>
        <el-button type="primary" @click="createHall">建立</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { hallApi, boothApi } from '../api'

const halls = ref([])
const booths = ref([])
const pick = ref(null)
const editForm = ref({})
const hallDlg = ref(false)
const newHall = ref({})

function statusClass(s) {
  if (s === '启用') return 's-ok'
  if (s === '布展') return 's-warn'
  return 's-off'
}

function boothsOf(hallId) {
  return booths.value.filter((b) => b.hallId === hallId)
}

function usedArea(hallId) {
  return boothsOf(hallId).reduce((s, b) => s + b.area, 0)
}

function fill(h) {
  if (!h.area) return 0
  return Math.min((usedArea(h.id) / h.area) * 100, 100)
}

function hallName(id) {
  const hit = halls.value.find((h) => h.id === id)
  return hit ? hit.name : id
}

function cellStyle(b) {
  // 面积越大格子越大：80㎡ 起，每 100㎡ 加一档宽度
  const w = Math.min(120 + b.area / 5, 260)
  return { width: w + 'px' }
}

async function load() {
  try {
    halls.value = await hallApi.list({})
    booths.value = await boothApi.list({})
    if (pick.value) {
      const hit = booths.value.find((b) => b.id === pick.value.id)
      if (hit) {
        pick.value = hit
        editForm.value = { id: hit.id, kind: hit.kind, status: hit.status === '已租' ? '空闲' : hit.status }
      }
    }
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openHall() {
  newHall.value = { area: 1000, status: '启用' }
  hallDlg.value = true
}

async function createHall() {
  try {
    await hallApi.create(newHall.value)
    ElMessage.success('已建立')
    hallDlg.value = false
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function saveBooth() {
  try {
    await boothApi.update(editForm.value.id, { kind: editForm.value.kind, status: editForm.value.status })
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(load)
</script>

<style scoped>
.plan-page {
  position: relative;
}
.plan-tools {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 14px;
}
.tools-title {
  font-size: 15px;
  font-weight: 600;
}
.legend {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: #6b7280;
}
.legend i {
  display: inline-block;
  width: 11px;
  height: 11px;
  border-radius: 3px;
  margin-right: 5px;
  vertical-align: -1px;
}
.sw.free {
  background: #eef7e9;
  border: 1px solid #b7dd93;
}
.sw.rented {
  background: #fdeef3;
  border: 1px solid #ee80a6;
}
.sw.fix {
  background: #f4f4f5;
  border: 1px solid #c8c9cc;
}
.plan-tools .el-button {
  margin-left: auto;
}
.plan-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.hall-block {
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 14px 16px 16px;
}
.hall-block.closed {
  opacity: 0.66;
  border-style: dashed;
}
.hall-head {
  display: flex;
  align-items: baseline;
  gap: 12px;
}
.hall-code {
  font-family: Menlo, monospace;
  font-size: 13px;
  color: var(--el-color-primary);
}
.hall-name {
  font-size: 15px;
  font-weight: 600;
}
.hall-area {
  font-size: 13px;
  color: #6b7280;
}
.hall-st {
  margin-left: auto;
  font-size: 12px;
  border-radius: 5px;
  padding: 2px 9px;
}
.hall-st.s-ok {
  background: #f0f9eb;
  color: #529b2e;
}
.hall-st.s-warn {
  background: #fdf6ec;
  color: #b88230;
}
.hall-st.s-off {
  background: #f4f4f5;
  color: #909399;
}
.hall-bar {
  height: 6px;
  background: #f2f4f9;
  border-radius: 4px;
  margin: 10px 0 14px;
  overflow: hidden;
}
.hall-fill {
  height: 100%;
  background: var(--el-color-primary-light-3);
  border-radius: 4px;
}
.booth-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  min-height: 74px;
  align-content: flex-start;
}
.booth-cell {
  border-radius: 8px;
  padding: 9px 11px;
  cursor: pointer;
  transition: transform 0.12s;
}
.booth-cell:hover {
  transform: translateY(-2px);
}
.booth-cell.free {
  background: #f4fbf0;
  border: 1px solid #b7dd93;
}
.booth-cell.rented {
  background: #fdeef3;
  border: 1px solid #ee80a6;
}
.booth-cell.fix {
  background: #f7f7f8;
  border: 1px dashed #c8c9cc;
}
.booth-cell.picked {
  box-shadow: 0 0 0 2px var(--el-color-primary);
}
.bc-code {
  font-family: Menlo, monospace;
  font-size: 12px;
  font-weight: 600;
}
.bc-area {
  font-size: 15px;
  font-weight: 600;
  color: #3a4152;
  line-height: 1.3;
}
.bc-kind {
  font-size: 11px;
  color: #8b93a7;
}
.grid-empty {
  font-size: 12px;
  color: #b0b6c6;
  padding: 20px 0;
}
.float-card {
  position: fixed;
  right: 34px;
  top: 92px;
  width: 244px;
  background: #fff;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  box-shadow: 0 8px 26px rgba(60, 70, 100, 0.13);
  padding: 14px 16px 16px;
}
.fc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.fc-code {
  font-family: Menlo, monospace;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary);
}
.fc-close {
  color: #b0b6c6;
  cursor: pointer;
  font-size: 13px;
}
.fc-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  padding: 4px 0;
  color: #6b7280;
}
.fc-row b {
  color: #3a4152;
}
.fc-form {
  margin-top: 8px;
}
.unit {
  margin-left: 8px;
  color: #8b93a7;
  font-size: 13px;
}
</style>
