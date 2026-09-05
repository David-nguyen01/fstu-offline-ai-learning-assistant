import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import AuthAlert from '../components/auth/AuthAlert.jsx'
import AuthInput from '../components/auth/AuthInput.jsx'
import AuthShell from '../components/auth/AuthShell.jsx'
import Button from '../components/common/Button.jsx'
import { resetPassword } from '../services/authService.js'

const highlights = [
  'Liên kết chỉ dùng được một lần',
  'Tự hết hạn sau 30 phút',
  'Không gửi mật khẩu qua email',
]

function ResetPasswordPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') || ''
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [formError, setFormError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  async function handleSubmit(event) {
    event.preventDefault()
    setFormError('')
    setSuccessMessage('')
    if (!token) return setFormError('Liên kết đặt lại mật khẩu không hợp lệ hoặc bị thiếu token.')
    if (password.length < 8) return setFormError('Mật khẩu mới phải có ít nhất 8 ký tự.')
    if (password !== confirmPassword) return setFormError('Hai mật khẩu không khớp.')

    setIsLoading(true)
    try {
      const message = await resetPassword({ token, newPassword: password })
      setSuccessMessage(message || 'Đặt lại mật khẩu thành công. Bạn có thể đăng nhập ngay.')
      setPassword('')
      setConfirmPassword('')
    } catch (error) {
      setFormError(error.message || 'Không thể đặt lại mật khẩu. Liên kết có thể đã hết hạn.')
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthShell
      asideAlign="right"
      asideEyebrow="Khôi phục tài khoản"
      asideText="Đặt mật khẩu mới bằng liên kết bảo mật được gửi tới email của bạn."
      asideTitle="Quay lại không gian học tập của bạn."
      highlights={highlights}
    >
      <div className="animate-auth-field animation-delay-225">
        <p className="text-sm font-extrabold text-primary">Đặt lại mật khẩu</p>
        <h2 className="mt-2 text-4xl font-extrabold tracking-tight">Tạo mật khẩu mới</h2>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">
          Mật khẩu hiện tại chỉ thay đổi sau khi liên kết hợp lệ được xác nhận.
        </p>
      </div>

      <form className="mt-9 space-y-5" onSubmit={handleSubmit}>
        {successMessage ? <AuthAlert tone="success">{successMessage}</AuthAlert> : null}
        {formError ? <AuthAlert>{formError}</AuthAlert> : null}
        <AuthInput
          label="Mật khẩu mới"
          name="password"
          onChange={(event) => setPassword(event.target.value)}
          type="password"
          value={password}
        />
        <AuthInput
          label="Nhập lại mật khẩu mới"
          name="confirmPassword"
          onChange={(event) => setConfirmPassword(event.target.value)}
          type="password"
          value={confirmPassword}
        />
        <Button className="h-13 w-full rounded-full text-base" disabled={isLoading || !token} type="submit" variant="cta">
          {isLoading ? 'Đang cập nhật...' : 'Đặt lại mật khẩu'}
        </Button>
      </form>

      <p className="mt-8 text-center text-sm text-muted-foreground">
        <Link className="font-extrabold text-primary" to="/login">Quay lại đăng nhập</Link>
      </p>
    </AuthShell>
  )
}

export default ResetPasswordPage
