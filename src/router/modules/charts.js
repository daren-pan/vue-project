/** When your routing table is too long, you can split it into small modules**/

import Layout from '@/layout'

const chartsRouter = {
  path: '/route',
  component: Layout,
  redirect: 'noRedirect',
  name: 'Route',
  meta: {
    title: '路由控制',
    icon: 'tree'
  },
  children: [
    {
      path: 'show_paths',
      component: () => import('@/views/route/show_paths'),
      name: 'ShowPaths',
      meta: { title: '查看最短路径', noCache: true }
    },
    {
      path: 'show_sid',
      component: () => import('@/views/route/show_sid'),
      name: 'ShowSID',
      meta: { title: '查看SID', noCache: true }
    },
    {
      path: 'show_policy',
      component: () => import('@/views/route/show_policy.vue'),
      name: 'ShowPolicy',
      meta: { title: '路由策略管理', noCache: true }
    },
    {
      path: 'show_steer',
      component: () => import('@/views/route/show_steer'),
      name: 'ShowSteer',
      meta: { title: '引导策略管理', noCache: true }
    }
  ]
}

export default chartsRouter
