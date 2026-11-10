import { useState } from 'react';
import api from '../api/axiosInstance';
import { useNotification } from './notificationContext';

// "Resend email": asks for a new confirmation link. The answer is the same whether or not the email has an account.
const ResendEmailButton = ({ email }) => {
    const [isSending, setIsSending] = useState(false);
    const { notify } = useNotification();

    const handleResend = async () => {
        if (!email) {
            notify('Enter your email address first.', 'error');
            return;
        }
        setIsSending(true);
        try {
            const response = await api.post('/auth/resend-verification', { email: email });
            notify(response.data.message);
        } catch (error) {
            let message = 'We could not send a new link. Please try again.';
            if (error.response && error.response.data && error.response.data.message) {
                message = error.response.data.message;
            }
            notify(message, 'error');
        } finally {
            setIsSending(false);
        }
    };

    return (
        <button type="button" className="button button--secondary" onClick={handleResend} disabled={isSending}>
            {isSending ? 'Sending…' : 'Resend email'}
        </button>
    );
};

export default ResendEmailButton;
