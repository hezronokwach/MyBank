import { useState } from 'react';
import api from '../api/axiosInstance';
import { useNavigate } from 'react-router-dom';
import { useNotification } from './notificationContext';

const DeleteAccountForm = ({ accountNumber }) => {
    const [pin, setPin] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const navigate = useNavigate();
    const { notify } = useNotification();
    
    const handleDelete = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            await api.delete(`/accounts/${accountNumber}`, { data: { pin } });
            notify('Account closed.');
            navigate('/dashboard');
        } catch (error) {
            notify(error.response?.data?.message || 'We could not delete your account. Please try again.', 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleDelete} className="transaction-form">
            <p className="eyebrow">Accounts</p>
            <h1>Close this account</h1>
            <p className="form-warning">Closing account •••{accountNumber ? accountNumber.slice(-4) : ''} can't be undone. Enter your PIN to confirm.</p>
            <label htmlFor="pin">PIN</label>
            <input id="pin" type="password" value={pin} onChange={(e) => setPin(e.target.value)} required />
            <button type="submit" className="button button--danger" disabled={isSubmitting}>{isSubmitting ? 'Closing…' : 'Close account'}</button>
        </form>
    );
};

export default DeleteAccountForm;
