import { useState } from 'react';
import api from '../api/axiosInstance';
import { useNavigate } from 'react-router-dom';
import { useNotification } from './notificationContext';

const CreateAccountForm = () => {
    const [type, setType] = useState('SAVINGS');
    const [currency, setCurrency] = useState('USD');
    const [pin, setPin] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const navigate = useNavigate();
    const { notify } = useNotification();
    
    const handleCreate = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            await api.post('/accounts', { type, currency, pin });
            notify('Account created successfully.');
            navigate('/dashboard');
        } catch (error) {
            notify(error.response?.data?.message || 'We could not create your account. Please try again.', 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleCreate} className="transaction-form">
            <p className="eyebrow">Accounts</p>
            <h1>Create new account</h1>
            <label htmlFor="type">Account Type</label>
            <select id="type" value={type} onChange={(e) => setType(e.target.value)}>
                <option value="SAVINGS">Savings</option>
                <option value="CURRENT">Current</option>
                <option value="FIXED_01">Fixed 01</option>
                <option value="FIXED_02">Fixed 02</option>
                <option value="FIXED_03">Fixed 03</option>
            </select>
            <label htmlFor="currency">Currency</label>
            <input id="currency" type="text" value={currency} onChange={(e) => setCurrency(e.target.value)} required />
            <label htmlFor="pin">PIN</label>
            <input id="pin" type="password" value={pin} onChange={(e) => setPin(e.target.value)} required />
            <button type="submit" className="button button--primary" disabled={isSubmitting}>{isSubmitting ? 'Processing…' : 'Create account'}</button>
        </form>
    );
};

export default CreateAccountForm;
