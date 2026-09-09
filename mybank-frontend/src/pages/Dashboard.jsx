import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';

const Dashboard = () => {
    const [accounts, setAccounts] = useState([]);
    const navigate = useNavigate();

    useEffect(() => {
        const fetchAccounts = async () => {
            try {
                const response = await api.get('/accounts/me');
                setAccounts(response.data);
            } catch (error) {
                console.error('Failed to fetch accounts', error);
            }
        };
        fetchAccounts();
    }, []);
    
    const account = accounts.length > 0 ? accounts[0] : null;

    return (
        <div className="p-8">
            <h1 className="text-3xl font-bold">Welcome</h1>
            {account ? (
                <div className="p-6 mt-6 bg-gray-100 rounded-lg shadow">
                    <p className="text-xl">Your Balance: 
                        <span className="font-bold"> {account.balance} {account.currency}</span>
                    </p>
                    <p>Account Number: {account.accountNumber}</p>
                    <p>Status: <span className={`font-bold ${account.status !== 'ACTIVE' ? 'text-red-500' : 'text-green-500'}`}>{account.status}</span></p>
                </div>
            ) : (
                <p className="mt-4">No accounts found.</p>
            )}
            
            <div className="flex gap-4 mt-6">
                <button onClick={() => navigate('/transfer')} className="px-4 py-2 text-white bg-green-500 rounded">Transfer</button>
                <button onClick={() => navigate('/deposit', { state: { accountNumber: account?.accountNumber } })} className="px-4 py-2 text-white bg-blue-500 rounded">Deposit</button>
                <button onClick={() => navigate('/withdraw', { state: { accountNumber: account?.accountNumber } })} className="px-4 py-2 text-white bg-red-500 rounded">Withdraw</button>
            </div>
        </div>
    );
};

export default Dashboard;
