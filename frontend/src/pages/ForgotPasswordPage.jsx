import { useState } from 'react'
import { Link } from 'react-router-dom'
import AuthAlert from '../components/auth/AuthAlert.jsx'
import AuthInput from '../components/auth/AuthInput.jsx'
import AuthShell from '../components/auth/AuthShell.jsx'
import Button from '../components/common/Button.jsx'
import { forgotPassword } from '../services/authService.js'

const highlights = [
  'Nhập email tài khoản',
  'Nhận liên kết bảo mật qua email',
  'Liên kết dùng một lần và tự hết hạn',
]

function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [isLoading, setIsLoading] = useState(false)

  function validateEmail() {
    const normalizedEmail = email.trim()
    const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

    if (!normalizedEmail) return 'Email is required.'
    if (!emailPattern.test(normalizedEmail)) return 'Enter a valid email address.'
    return ''
  }

  async function handleSubmit(event) {
    event.preventDefault()
    const validationError = validateEmail()
    if (validationError) {
      setError(validationError)
      setMessage('')
      return
    }

    setIsLoading(true)
    setError('')
    setMessage('')

    try {
      const responseMessage = await forgotPassword(email.trim())
      setMessage(responseMessage || 'Nếu email tồn tại, liên kết đặt lại mật khẩu đã được gửi.')
    } catch (requestError) {
      setError(requestError.message)
    } finally {
      setIsLoading(false)
    }
  }

  return (
    <AuthShell
      asideEyebrow="Khôi phục tài khoản"
      asideText="FStu sẽ gửi liên kết đặt lại mật khẩu dùng một lần tới email tài khoản."
      asideTitle="Quay lại không gian học tập của bạn."
      highlights={highlights}
    >
      <div className="animate-auth-field animation-delay-225">
        <p className="text-sm font-extrabold text-primary">Hỗ trợ mật khẩu</p>
        <h1 className="mt-2 text-4xl font-extrabold tracking-tight">
          Đặt lại mật khẩu
        </h1>
        <p className="mt-3 text-sm leading-6 text-muted-foreground">
          Nhập email và kiểm tra hộp thư để mở liên kết đặt lại mật khẩu.
        </p>
      </div>

      <form className="mt-9 space-y-5" onSubmit={handleSubmit}>
        {error ? <AuthAlert>{error}</AuthAlert> : null}
        {message ? <AuthAlert>{message}</AuthAlert> : null}

        <div className="animate-auth-field animation-delay-300">
          <AuthInput
            error={error && !message ? error : ''}
            label="Email"
            name="email"
            onChange={(event) => {
              setEmail(event.target.value)
              setError('')
              setMessage('')
            }}
            placeholder="student@fpt.edu.vn"
            type="email"
            value={email}
          />
        </div>

        <div className="animate-auth-field animation-delay-450">
          <Button
            className="h-13 w-full rounded-full text-base"
            disabled={isLoading}
            type="submit"
            variant="cta"
          >
            {isLoading ? 'Đang gửi...' : 'Gửi liên kết đặt lại'}
          </Button>
        </div>
      </form>

      <p className="animate-auth-field animation-delay-450 mt-8 text-center text-sm text-muted-foreground">
        Remember your password?{' '}
        <Link className="font-extrabold text-primary" to="/login">
          Log in
        </Link>
      </p>
    </AuthShell>
  )
}

export default ForgotPasswordPage
