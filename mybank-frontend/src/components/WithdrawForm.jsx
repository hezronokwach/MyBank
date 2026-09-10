import React, { useState } from 'react';
import api from '../api/axiosInstance';

const WithdrawForm = ({ accountNumber }) => {
    const [amount, setAmount] = useState('');
    
    const handleWithdraw = async (e) => {
        e.preventDefault();
        try {
            await api.patch(`/accounts/${accountNumber}/withdrawals`, { amount });
            alert('Withdraw successful');
        } catch (error) {
            alert('Withdraw failed');
        }
    };

    return (
        <form onSubmit={handleWithdraw} className="max-w-sm p-6 bg-white rounded-lg shadow-md">
            <h2 className="mb-4 text-xl font-bold text-bank-heading">Withdraw Funds</h2>
            <input 
                type="number" 
                placeholder="Amount" 
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-full p-2 mb-4 border rounded" 
            />
            <button type="submit" className="w-full p-2 text-white bg-red-600 rounded hover:bg-red-700">Withdraw</button>
        </form>
    );
};

export default WithdrawForm;
