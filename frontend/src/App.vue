<template>
  <div class="app-shell">
    <div class="crumb-bar">
      <div class="back-btn" @click="goBack">
        <span class="arrow">←</span>
        <span>返回</span>
      </div>
      <div class="crumbs">
        <span class="crumb-root" @click="go('/halls')">会展中心</span>
        <span class="sep">›</span>
        <span class="crumb-cur">
          <el-dropdown trigger="click" @command="go">
            <span class="crumb-pick">
              {{ currentLabel }}
              <span class="caret">▾</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-for="m in modules" :key="m.path" :command="m.path">
                  {{ m.label }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </span>
      </div>
      <div class="crumb-tip">展位与展具排期</div>
    </div>
    <div class="app-body">
      <router-view />
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const modules = [
  { path: '/halls', label: '展馆平面图' },
  { path: '/booths', label: '展位管理' },
  { path: '/bookings', label: '展会排期' },
  { path: '/road-closures', label: '封道申报' },
  { path: '/equipment', label: '展具台账' },
  { path: '/calibrations', label: '校准批次' }
]

const currentLabel = computed(() => {
  const hit = modules.find((m) => m.path === route.path)
  return hit ? hit.label : '展馆平面图'
})

function go(path) {
  router.push(path)
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/halls')
  }
}
</script>

<style>
.app-shell {
  min-height: 100vh;
  background: #fdfafc;
}
.crumb-bar {
  display: flex;
  align-items: center;
  gap: 18px;
  padding: 12px 26px;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.back-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 13px;
  color: #6b7280;
  border: 1px solid var(--el-border-color);
  border-radius: 16px;
  padding: 5px 14px 5px 11px;
  cursor: pointer;
}
.back-btn:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}
.arrow {
  font-size: 14px;
}
.crumbs {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 14px;
}
.crumb-root {
  color: #98a0b5;
  cursor: pointer;
}
.crumb-root:hover {
  color: var(--el-color-primary);
}
.sep {
  color: #ccd1de;
}
.crumb-cur {
  font-weight: 600;
}
.crumb-pick {
  color: var(--el-color-primary);
  cursor: pointer;
  outline: none;
}
.caret {
  font-size: 11px;
  margin-left: 2px;
}
.crumb-tip {
  margin-left: auto;
  font-size: 12px;
  color: #b0b6c6;
}
.app-body {
  padding: 18px 26px 34px;
}
</style>
