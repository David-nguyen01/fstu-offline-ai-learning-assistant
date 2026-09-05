import { lazy, Suspense, useEffect, useState } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { UploadProgressPopup } from './components/UploadProgressPopup.jsx'
import {
  getDefaultRouteForUser,
  getSavedUser,
  isAdminSession,
  isAuthenticated,
  isResearchStaffSession,
} from './services/authService.js'
import { resumeActiveUploads } from './services/uploadService.js'

const LandingPage = lazy(() => import('./pages/LandingPage.jsx'))
const ForgotPasswordPage = lazy(() => import('./pages/ForgotPasswordPage.jsx'))
const LoginPage = lazy(() => import('./pages/LoginPage.jsx'))
const RegisterPage = lazy(() => import('./pages/RegisterPage.jsx'))
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage.jsx'))
const SettingsPage = lazy(() => import('./pages/SettingsPage.jsx'))
const AdminLayout = lazy(() => import('./layouts/AdminLayout.jsx'))
const AdminDashboardPage = lazy(() => import('./pages/admin/AdminPages.jsx').then((module) => ({ default: module.AdminDashboardPage })))
const AdminDocumentsPage = lazy(() => import('./pages/admin/AdminPages.jsx').then((module) => ({ default: module.AdminDocumentsPage })))
const AdminUsersPage = lazy(() => import('./pages/admin/AdminPages.jsx').then((module) => ({ default: module.AdminUsersPage })))
const AdminResearchDashboardPage = lazy(() => import('./pages/admin/AdminResearchDashboardPage.jsx').then((module) => ({ default: module.AdminResearchDashboardPage })))
const AdminTestSetPage = lazy(() => import('./pages/admin/AdminTestSetPage.jsx').then((module) => ({ default: module.AdminTestSetPage })))
const DocumentDetailPage = lazy(() => import('./pages/DocumentDetailPage.jsx'))
const LibraryPage = lazy(() => import('./pages/LibraryPage.jsx'))
const NotFoundPage = lazy(() => import('./pages/NotFoundPage.jsx'))
const WorkspacePage = lazy(() => import('./pages/WorkspacePage.jsx'))
const SemesterWorkspacePage = lazy(() => import('./pages/admin/SemesterWorkspacePage.jsx'))
const AdminPaymentsPage = lazy(() => import('./pages/admin/AdminPaymentsPage.jsx'))
const PaymentResultPage = lazy(() => import('./pages/PaymentResultPage.jsx'))
const ProPlanPage = lazy(() => import('./pages/ProPlanPage.jsx'))
const PaymentsPage = lazy(() => import('./pages/PaymentsPage.jsx'))
const AdminPlansPage = lazy(() => import('./pages/admin/AdminPlansPage.jsx'))
const AdminFeedbackPage = lazy(() => import('./pages/admin/AdminFeedbackPage.jsx'))

function App() {
  const [, setAuthVersion] = useState(0)

  useEffect(() => {
    const handleUnauthorized = () => setAuthVersion((value) => value + 1)
    window.addEventListener('fstu:unauthorized', handleUnauthorized)
    return () => window.removeEventListener('fstu:unauthorized', handleUnauthorized)
  }, [])

  useEffect(() => {
    if (isAuthenticated()) {
      resumeActiveUploads()
    }
  }, [])

  return (
    <>
      <ScrollToTop />
      <UploadProgressPopup />
      <Suspense fallback={<div className="grid min-h-screen place-items-center text-sm font-semibold text-slate-600">Đang tải...</div>}>
      <Routes>
      <Route element={<LandingPage />} path="/" />
      <Route element={<PublicOnly><LoginPage /></PublicOnly>} path="/login" />
      <Route element={<PublicOnly><ForgotPasswordPage /></PublicOnly>} path="/forgot-password" />
      <Route element={<PublicOnly><RegisterPage /></PublicOnly>} path="/register" />
      <Route element={<PublicOnly><ResetPasswordPage /></PublicOnly>} path="/reset-password" />
      <Route element={<RequireAuth><SettingsPage /></RequireAuth>} path="/settings" />
      <Route element={<RequireAuth><SettingsPage /></RequireAuth>} path="/profile" />
      <Route element={<RequireAuth><ProPlanPage /></RequireAuth>} path="/pro" />
      <Route element={<RequireAuth><PaymentsPage /></RequireAuth>} path="/payments" />
      <Route element={<RequireAuth><PaymentResultPage /></RequireAuth>} path="/payment/result" />
      <Route element={<RequireResearchStaff><AdminLayout /></RequireResearchStaff>} path="/admin">
        <Route index element={<AdminIndex />} />
        <Route path="dashboard" element={<RequireAdmin><AdminDashboardPage /></RequireAdmin>} />
        <Route path="users" element={<RequireAdmin><AdminUsersPage /></RequireAdmin>} />
        <Route path="documents" element={<RequireAdmin><AdminDocumentsPage /></RequireAdmin>} />
        <Route path="courses" element={<RequireAdmin><SemesterWorkspacePage /></RequireAdmin>} />
        <Route path="subjects" element={<RequireAdmin><Navigate replace to="/admin/courses" /></RequireAdmin>} />
        <Route path="test-set" element={<AdminTestSetPage />} />
        <Route path="research-dashboard" element={<AdminResearchDashboardPage />} />
        <Route path="payments" element={<RequireAdmin><AdminPaymentsPage /></RequireAdmin>} />
        <Route path="plans" element={<RequireAdmin><AdminPlansPage /></RequireAdmin>} />
        <Route path="feedback" element={<RequireAdmin><AdminFeedbackPage /></RequireAdmin>} />
        {/* Redirects: old standalone pages → unified Research Dashboard */}
        <Route path="indexing" element={<Navigate replace to="/admin/research-dashboard" />} />
        <Route path="model-settings" element={<Navigate replace to="/admin/research-dashboard" />} />
        <Route path="experiments" element={<Navigate replace to="/admin/research-dashboard" />} />
        <Route path="logs" element={<Navigate replace to="/admin/research-dashboard" />} />
      </Route>
      <Route element={<RequireAuth><WorkspacePage /></RequireAuth>} path="/workspace" />
      <Route element={<RequireAuth><Navigate replace to="/workspace" /></RequireAuth>} path="/chat" />
      <Route element={<RequireAuth><Navigate replace to="/workspace" /></RequireAuth>} path="/app" />
      <Route element={<RequireAuth><LibraryPage /></RequireAuth>} path="/library" />
      <Route element={<RequireAuth><DocumentDetailPage /></RequireAuth>} path="/library/documents/:id" />
      <Route element={<RequireAuth><NotFoundPage /></RequireAuth>} path="*" />
      </Routes>
      </Suspense>
    </>
  )
}

function ScrollToTop() {
  const { pathname } = useLocation()

  useEffect(() => {
    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual'
    }
  }, [])

  useEffect(() => {
    const resetScroll = () => window.scrollTo({ top: 0, left: 0 })
    resetScroll()
    const frameId = window.requestAnimationFrame(resetScroll)
    const timeoutIds = [0, 50, 250].map((delay) => window.setTimeout(resetScroll, delay))
    return () => {
      window.cancelAnimationFrame(frameId)
      timeoutIds.forEach((timeoutId) => window.clearTimeout(timeoutId))
    }
  }, [pathname])

  return null
}

function PublicOnly({ children }) {
  if (isAuthenticated()) {
    return <Navigate replace to={getDefaultRouteForUser(getSavedUser())} />
  }

  return children
}

function RequireAuth({ children }) {
  const location = useLocation()
  if (!isAuthenticated()) {
    return <Navigate replace state={{ returnTo: `${location.pathname}${location.search}` }} to="/login" />
  }

  return children
}

function RequireAdmin({ children }) {
  if (!isAuthenticated()) {
    return <Navigate replace to="/login" />
  }

  if (!isAdminSession()) {
    return <Navigate replace to="/workspace" />
  }

  return children
}

function RequireResearchStaff({ children }) {
  if (!isAuthenticated()) return <Navigate replace to="/login" />
  if (!isResearchStaffSession()) return <Navigate replace to="/workspace" />
  return children
}

function AdminIndex() {
  return <Navigate replace to={isAdminSession() ? '/admin/dashboard' : '/admin/research-dashboard'} />
}

export default App
