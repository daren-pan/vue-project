/** When your routing table is too long, you can split it into small modules **/

import Layout from '@/layout'

const tableRouter = {
  path: '/deploy',
  component: Layout,
  redirect: '/deploy/app',
  name: 'Deploy',
  meta: {
    title: '算力请求',
    icon: 'edit'
  },
  children: [
    {
      path: 'app',
      component: () => import('@/views/deploy/app.vue'),
      name: 'DeployApp',
      meta: { title: '应用部署' }
    },
    {
      path: 'service',
      component: () => import('@/views/deploy/service.vue'),
      name: 'DeployService',
      meta: { title: '服务部署' }
    }
  ]
}
export default tableRouter
