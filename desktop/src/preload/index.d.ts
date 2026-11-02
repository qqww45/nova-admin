import type { HengZhangApi } from './index'

declare global {
  interface Window {
    hengzhang: HengZhangApi
  }
}

export {}
