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
            notify('Account deleted successfully.');
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
            <h1>Delete Account</h1>
            <p className="form-intro">Are you sure? This action cannot be undone.</p>
            <label htmlFor="pin">PIN</label>
            <input id="pin" type="password" value={pin} onChange={(e) => setPin(e.target.value)} required />
            <button type="submit" className="button button--danger" disabled={isSubmitting}>{isSubmitting ? 'Processing…' : 'Delete Account'}</button>
        </form>
    );
};

export default DeleteAccountForm;
