
import Home from '../../layout/index.vue'

const constRoutes = [
  {
    path: '/',
    name: 'home',
    component: Home,
    meta: { keepAlive: true },
    children: [
      {
        path: '/system/console',
        name: 'console-shardsys',
        component: () => import('../../views/console/shardsys/index.vue'),
      },
      {
        path: '/system/console',
        name: 'console-kv',
        component: () => import('../../views/console/kv/index.vue'),
      },
      {
        path: '/nodes/shardmaster',
        name: 'nodes-shardmaster',
        component: () => import('../../views/nodes/shardmaster/index.vue'),
      },
      {
        path: '/nodes/shardkv',
        name: 'nodes-shardkv',
        component: () => import('../../views/nodes/shardkv/index.vue'),
      },
      {
        path: '/raft/views',
        name: 'raft-views',
        component: () => import('../../views/raft/index.vue')
      },
      {
        path: '/consistency/views',
        name: 'consistency-views',
        component: () => import('../../views/consistency/index.vue')
      },
      {
        path: '/concurrent/views',
        name: 'concurrent-views',
        component: () => import('../../views/concurrent/index.vue')
      },
    ],
  },
  {
    path: '/error',
    name: 'error',
    component: () => import('../../components/error/404.vue'),
  },
]
export default constRoutes
