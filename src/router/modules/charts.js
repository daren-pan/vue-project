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
      meta: { title: '查看路由', noCache: true }
    },
    {
      path: 'show_steer',
      component: () => import('@/views/route/show_steer'),
      name: 'ShowSteer',
      meta: { title: '查看引导', noCache: true }
    },
    {
      path: 'add_policy',
      component: () => import('@/views/route/add_policy.vue'),
      name: 'AddPolicy',
      meta: { title: '添加路由', noCache: true }
    },
    {
      path: 'steer',
      component: () => import('@/views/route/steer.vue'),
      name: 'Steer',
      meta: { title: '更新引导', noCache: true }
    },
    {
      path: 'del_steer',
      component: () => import('@/views/route/del_steer.vue'),
      name: 'DelSteer',
      meta: { title: '删除引导', noCache: true }
    },
    {
      path: 'del_policy',
      component: () => import('@/views/route/del_policy.vue'),
      name: 'DelPolicy',
      meta: { title: '删除路由', noCache: true }
    }
  ]
}

export default chartsRouter
