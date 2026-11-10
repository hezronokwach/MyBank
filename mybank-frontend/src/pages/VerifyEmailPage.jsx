import { useState, useEffect, useRef } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import api from '../api/axiosInstance';
import ResendEmailButton from '../components/ResendEmailButton';

// /verify-email?token=...: the page the link in the confirmation email opens
const VerifyEmailPage = () => {
    const [searchParams] = useSearchParams();
    const token = searchParams.get('token');
    // "checking", "confirmed" or "failed"
    const [result, setResult] = useState('checking');
    const [email, setEmail] = useState('');
    // A link works only once. React runs this page's effect twice while developing (StrictMode),
    // so this remembers that we already sent it and stops the second try.
    const alreadySent = useRef(false);

    useEffect(() => {
        if (alreadySent.current) {
            return;
        }
        alreadySent.current = true;

        const confirm = async () => {
            if (!token) {
                setResult('failed');
                return;
            }
            try {
                await api.post('/auth/verify-email', { token: token });
                setResult('confirmed');
            } catch (error) {
                console.error('Could not confirm the email', error);
                setResult('failed');
            }
        };
        confirm();
    }, [token]);

    let content;
    if (result === 'checking') {
        content = (
            <>
                <h1>Confirming your email…</h1>
                <p className="form-intro">This only takes a moment.</p>
            </>
        );
    } else if (result === 'confirmed') {
        content = (
            <>
                <div className="auth-icon auth-icon--success" aria-hidden="true">✓</div>
                <h1>Your email is confirmed</h1>
                <p className="form-intro">You can sign in now. An admin will approve your account before you can move money.</p>
                <Link to="/login" className="button button--primary">Sign in</Link>
            </>
        );
    } else {
        content = (
            <>
                <div className="auth-icon auth-icon--error" aria-hidden="true">!</div>
                <h1>This link has expired or was already used</h1>
                <p className="form-intro">If you already confirmed, just sign in. Otherwise enter your email and we'll send a new link.</p>
                <label htmlFor="resend-email">Email address</label>
                <input id="resend-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
                <ResendEmailButton email={email} />
                <p className="form-switch"><Link to="/login">Back to sign in</Link></p>
            </>
        );
    }

    return (
        <div className="auth-page">
            <Link to="/" className="brand">MyBank</Link>
            <div className="auth-form">
                <p className="eyebrow">Email confirmation</p>
                {content}
            </div>
        </div>
    );
};

export default VerifyEmailPage;
