import { createRouter, createWebHashHistory } from 'vue-router'
import ConnectView from '../views/ConnectView.vue'
import SyncView from '../views/SyncView.vue'
import StatementView from '../views/StatementView.vue'
import AboutView from '../views/AboutView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/connect' },
    { path: '/connect', name: 'connect', component: ConnectView, meta: { step: 1 } },
    { path: '/sync', name: 'sync', component: SyncView, meta: { step: 2 } },
    { path: '/statement', name: 'statement', component: StatementView, meta: { step: 3 } },
    { path: '/about', name: 'about', component: AboutView, meta: { step: 0 } }
  ]
})

export default router
