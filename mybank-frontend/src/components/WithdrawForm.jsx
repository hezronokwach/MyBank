import { useState } from 'react';
import api from '../api/axiosInstance';
import { useNavigate } from 'react-router-dom';
import { useNotification } from './notificationContext';

const WithdrawForm = ({ accountNumber }) => {
    const [amount, setAmount] = useState('');
    const [pin, setPin] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const navigate = useNavigate();
    const { notify } = useNotification();
    
    const handleWithdraw = async (e) => {
        e.preventDefault();
        if (!accountNumber) return navigate('/dashboard');
        setIsSubmitting(true);
        try {
            await api.patch(`/accounts/${accountNumber}/withdrawals`, { amount, pin });
            notify('Withdrawal completed. Your balance has been updated.');
            navigate('/dashboard');
        } catch (error) {
            notify(error.response?.data?.message || 'We could not complete your withdrawal. Please try again.', 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleWithdraw} className="transaction-form">
            <p className="eyebrow">Take money out</p>
            <h1>Withdraw funds</h1>
            <p className="form-intro">Enter the amount you would like to withdraw from this account.</p>
            <label htmlFor="withdraw-amount">Amount</label>
            <input 
                id="withdraw-amount"
                type="number" 
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
            />
            <label htmlFor="withdraw-pin">PIN</label>
            <input 
                id="withdraw-pin"
                type="password" 
                value={pin}
                onChange={(e) => setPin(e.target.value)}
                maxLength="4"
                required
            />
            <button type="submit" className="button button--primary" disabled={isSubmitting}>{isSubmitting ? 'Processing…' : 'Withdraw funds'}</button>
        </form>
    );
};

export default WithdrawForm;
