import { createRouter, createWebHistory } from 'vue-router'
import Halls from '../views/Halls.vue'
import Booths from '../views/Booths.vue'
import Bookings from '../views/Bookings.vue'
import RoadClosures from '../views/RoadClosures.vue'
import Equipment from '../views/Equipment.vue'
import Calibrations from '../views/Calibrations.vue'

const routes = [
  { path: '/', redirect: '/halls' },
  { path: '/halls', component: Halls, meta: { title: '展馆平面图' } },
  { path: '/booths', component: Booths, meta: { title: '展位管理' } },
  { path: '/bookings', component: Bookings, meta: { title: '展会排期' } },
  { path: '/road-closures', component: RoadClosures, meta: { title: '封道申报' } },
  { path: '/equipment', component: Equipment, meta: { title: '展具台账' } },
  { path: '/calibrations', component: Calibrations, meta: { title: '校准批次' } }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
