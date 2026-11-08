/** Real APIs are the default. Mock data is never a network-error fallback. */
export const useMock = import.meta.env.VITE_USE_MOCK === 'true'
export const apiBase = import.meta.env.VITE_API_BASE_URL || '/api/v1'
