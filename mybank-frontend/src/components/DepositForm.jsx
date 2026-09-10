import React, { useState } from 'react';
import api from '../api/axiosInstance';

const DepositForm = ({ accountNumber }) => {
    const [amount, setAmount] = useState('');
    
    const handleDeposit = async (e) => {
        e.preventDefault();
        try {
            await api.post(`/accounts/${accountNumber}/deposits`, { amount });
            alert('Deposit successful');
        } catch (error) {
            alert('Deposit failed');
        }
    };

    return (
        <form onSubmit={handleDeposit} className="max-w-sm p-6 bg-white rounded-lg shadow-md">
            <h2 className="mb-4 text-xl font-bold text-bank-heading">Deposit Funds</h2>
            <input 
                type="number" 
                placeholder="Amount" 
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-full p-2 mb-4 border rounded" 
            />
            <button type="submit" className="w-full p-2 text-white bg-green-600 rounded hover:bg-green-700">Deposit</button>
        </form>
    );
};

export default DepositForm;
