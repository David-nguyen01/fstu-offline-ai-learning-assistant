import { afterEach, describe, expect, it } from 'vitest'
import {
  ADMIN_ROLE,
  RESEARCHER_ROLE,
  STUDENT_ROLE,
  TEACHER_ROLE,
  clearSession,
  getDefaultRouteForUser,
  getSavedUser,
  hasRole,
  normalizeRoles,
} from './authService.js'

afterEach(() => clearSession())

describe('frontend role canonicalization', () => {
  it('keeps administrator access when any backend role is ADMIN', () => {
    expect(normalizeRoles(['STUDENT', 'ADMIN', 'RESEARCHER'])).toEqual([ADMIN_ROLE, RESEARCHER_ROLE, STUDENT_ROLE])
    expect(getDefaultRouteForUser({ roles: ['TEACHER', 'ADMIN'] })).toBe('/admin/dashboard')
  })

  it('preserves supported backend roles and maps only USER to Student', () => {
    expect(normalizeRoles(['RESEARCHER'])).toEqual([RESEARCHER_ROLE])
    expect(normalizeRoles(['TEACHER'])).toEqual([TEACHER_ROLE])
    expect(normalizeRoles(['USER'])).toEqual([STUDENT_ROLE])
    expect(hasRole({ roles: ['RESEARCHER'] }, 'RESEARCHER')).toBe(true)
    expect(getDefaultRouteForUser({ roles: ['RESEARCHER'] })).toBe('/admin/research-dashboard')
  })

  it('normalizes a legacy saved session before route checks and display', () => {
    localStorage.setItem('fstu_user', JSON.stringify({
      id: 'legacy-user',
      name: 'Legacy researcher',
      role: 'researcher',
      roles: ['RESEARCHER'],
    }))

    expect(getSavedUser()).toMatchObject({
      id: 'legacy-user',
      role: 'researcher',
      roles: [RESEARCHER_ROLE],
    })
  })
})
