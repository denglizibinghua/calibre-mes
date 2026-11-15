/** GET /admin/pro-work-order/{id}/qr-code 的真实工单码元数据。 */
export interface WorkOrderQrCodeVO {
  workOrderId: number
  workOrderNo: string
  content: string
}
