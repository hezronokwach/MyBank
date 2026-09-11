import { useState } from 'react';
import api from '../api/axiosInstance';
import { useNavigate } from 'react-router-dom';
import { useNotification } from './notificationContext';

const DepositForm = ({ accountNumber }) => {
    const [amount, setAmount] = useState('');
    const [pin, setPin] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const navigate = useNavigate();
    const { notify } = useNotification();
    
    const handleDeposit = async (e) => {
        e.preventDefault();
        if (!accountNumber) return navigate('/dashboard');
        setIsSubmitting(true);
        try {
            await api.patch(`/accounts/${accountNumber}/deposits`, { amount, pin });
            notify('Deposit completed. Your balance has been updated.');
            navigate('/dashboard');
        } catch (error) {
            notify(error.response?.data?.message || 'We could not complete your deposit. Please try again.', 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleDeposit} className="transaction-form">
            <p className="eyebrow">Add money</p>
            <h1>Deposit funds</h1>
            <p className="form-intro">Enter the amount you would like to add to this account.</p>
            <label htmlFor="deposit-amount">Amount</label>
            <input 
                id="deposit-amount"
                type="number" 
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
            />
            <label htmlFor="deposit-pin">PIN</label>
            <input 
                id="deposit-pin"
                type="password" 
                value={pin}
                onChange={(e) => setPin(e.target.value)}
                maxLength="4"
                required
            />
            <button type="submit" className="button button--primary" disabled={isSubmitting}>{isSubmitting ? 'Processing…' : 'Deposit funds'}</button>
        </form>
    );
};

export default DepositForm;
