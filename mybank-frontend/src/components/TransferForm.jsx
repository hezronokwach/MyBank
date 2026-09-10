import React, { useState } from 'react';
import api from '../api/axiosInstance';

const TransferForm = () => {
    const [fromAccountNumber, setFromAccountNumber] = useState('');
    const [toAccountNumber, setToAccountNumber] = useState('');
    const [amount, setAmount] = useState('');
    
    const handleTransfer = async (e) => {
        e.preventDefault();
        try {
            const key = crypto.randomUUID();
            await api.patch('/transfers', 
                { fromAccountNumber, toAccountNumber, amount },
                { headers: { 'Idempotency-Key': key } }
            );
            alert('Transfer successful');
        } catch (error) {
            alert('Transfer failed');
        }
    };

    return (
        <form onSubmit={handleTransfer} className="max-w-md p-6 bg-white rounded-lg shadow-md">
            <h2 className="mb-4 text-xl font-bold text-bank-heading">Transfer Funds</h2>
            <input 
                type="text" 
                placeholder="From Account" 
                value={fromAccountNumber}
                onChange={(e) => setFromAccountNumber(e.target.value)}
                className="w-full p-2 mb-3 border rounded" 
            />
            <input 
                type="text" 
                placeholder="To Account" 
                value={toAccountNumber}
                onChange={(e) => setToAccountNumber(e.target.value)}
                className="w-full p-2 mb-3 border rounded" 
            />
            <input 
                type="number" 
                placeholder="Amount" 
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-full p-2 mb-4 border rounded" 
            />
            <button type="submit" className="w-full p-2 text-white bg-blue-600 rounded hover:bg-blue-700">Send</button>
        </form>
    );
};

export default TransferForm;
