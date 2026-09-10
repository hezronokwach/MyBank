import { useState } from 'react';
import api from '../api/axiosInstance';
import { useNavigate } from 'react-router-dom';
import { useNotification } from './notificationContext';

const TransferForm = () => {
    const [fromAccountNumber, setFromAccountNumber] = useState('');
    const [toAccountNumber, setToAccountNumber] = useState('');
    const [amount, setAmount] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const navigate = useNavigate();
    const { notify } = useNotification();
    
    const handleTransfer = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            const key = crypto.randomUUID();
            await api.patch('/transfers', 
                { fromAccountNumber, toAccountNumber, amount },
                { headers: { 'Idempotency-Key': key } }
            );
            notify('Transfer sent successfully. Your balance has been updated.');
            navigate('/dashboard');
        } catch (error) {
            notify(error.response?.data?.message || 'We could not complete your transfer. Please check the details and try again.', 'error');
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <form onSubmit={handleTransfer} className="transaction-form">
            <p className="eyebrow">Move money</p>
            <h1>Transfer funds</h1>
            <p className="form-intro">Send money between MyBank accounts securely.</p>
            <label htmlFor="from-account">From account</label>
            <input 
                id="from-account"
                type="text" 
                value={fromAccountNumber}
                onChange={(e) => setFromAccountNumber(e.target.value)}
                required
            />
            <label htmlFor="to-account">To account</label>
            <input 
                id="to-account"
                type="text" 
                value={toAccountNumber}
                onChange={(e) => setToAccountNumber(e.target.value)}
                required
            />
            <label htmlFor="transfer-amount">Amount</label>
            <input 
                id="transfer-amount"
                type="number" 
                min="0.01"
                step="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                required
            />
            <button type="submit" className="button button--primary" disabled={isSubmitting}>{isSubmitting ? 'Sending…' : 'Send transfer'}</button>
        </form>
    );
};

export default TransferForm;
