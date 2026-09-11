import { useState } from 'react';
import api from '../api/axiosInstance';
import { useNavigate } from 'react-router-dom';
import { useNotification } from './notificationContext';

const TransferOwnershipForm = ({ accountNumber }) => {
    const [newOwnerEmail, setNewOwnerEmail] = useState('');
    const [pin, setPin] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const navigate = useNavigate();
    const { notify } = useNotification();
    
    const handleTransfer = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            await api.patch(`/accounts/${accountNumber}/transfer`, { newOwnerEmail, pin });
            notify('Ownership transferred successfully.');
            navigate('/dashboard');
        } catch (error) {
            notify(error.response?.data?.message || 'We could not transfer ownership. Please try again.', 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleTransfer} className="transaction-form">
            <p className="eyebrow">Accounts</p>
            <h1>Transfer Ownership</h1>
            <label htmlFor="email">New Owner Email</label>
            <input id="email" type="email" value={newOwnerEmail} onChange={(e) => setNewOwnerEmail(e.target.value)} required />
            <label htmlFor="pin">PIN</label>
            <input id="pin" type="password" value={pin} onChange={(e) => setPin(e.target.value)} required />
            <button type="submit" className="button button--primary" disabled={isSubmitting}>{isSubmitting ? 'Processing…' : 'Transfer Ownership'}</button>
        </form>
    );
};

export default TransferOwnershipForm;
