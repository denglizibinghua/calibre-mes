import request from './request'

export function getFaceTemplateStatus() {
  return request.get<boolean>('/admin/user/face-template')
}

export function registerFaceTemplate(data: { imageBase64: string; consent: boolean }) {
  return request.post<void>('/admin/user/face-template', data)
}

export function revokeFaceTemplate() {
  return request.delete<void>('/admin/user/face-template')
}
