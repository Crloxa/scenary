import instance, { unwrap } from '@/utils/request'

// P18 举报（docs/02 §10.1）
export const reportApi = {
  async create({ targetType, targetId, reasonCode, reasonText = '' }) {
    return unwrap(
      await instance.post('/reports', { targetType, targetId, reasonCode, reasonText }),
    )
  },
}
