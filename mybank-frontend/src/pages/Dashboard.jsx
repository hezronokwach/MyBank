import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';
import Layout from '../components/Layout';

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
        <Layout>
            <div className="max-w-5xl mx-auto px-4 py-8">
                <div className="flex items-center justify-between mb-8">
                    <h1 className="text-3xl font-extrabold text-bank-heading tracking-tight">Your Dashboard</h1>
                    <span className="text-sm text-gray-500 font-medium">Welcome back</span>
                </div>
                {account ? (
                    <div className="bg-white border border-gray-200 rounded-lg p-8">
                        <div className="mb-6 border-b border-gray-100 pb-6">
                            <p className="text-sm text-gray-500 uppercase tracking-wider font-semibold">Total Balance</p>
                            <p className="text-4xl font-bold mt-2 text-bank-heading">{account.balance} <span className="text-2xl text-gray-500">{account.currency}</span></p>
                        </div>
                        
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                            <div>
                                <p className="text-xs text-gray-500 uppercase tracking-wider font-semibold mb-1">Account Number</p>
                                <p className="font-semibold text-bank-text">{account.accountNumber}</p>
                            </div>
                            <div>
                                <p className="text-xs text-gray-500 uppercase tracking-wider font-semibold mb-1">Account Type</p>
                                <p className="font-semibold text-bank-text capitalize">{account.type.toLowerCase()}</p>
                            </div>
                            <div>
                                <p className="text-xs text-gray-500 uppercase tracking-wider font-semibold mb-1">Status</p>
                                <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-semibold ${account.status === 'ACTIVE' ? 'bg-green-50 text-green-700' : 'bg-amber-50 text-amber-700'}`}>
                                    {account.status}
                                </span>
                            </div>
                        </div>
                    </div>
                ) : (
                    <div className="text-center py-20 bg-white rounded border border-gray-200">
                        <p className="text-gray-500">No active accounts found.</p>
                    </div>
                )}
                
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-8">
                    <button onClick={() => navigate('/transfer')} className="py-2.5 text-white bg-indigo-600 rounded hover:bg-indigo-700 transition font-semibold">Transfer</button>
                    <button onClick={() => navigate('/deposit', { state: { accountNumber: account?.accountNumber } })} className="py-2.5 text-gray-700 bg-white border border-gray-200 rounded hover:bg-gray-50 transition font-semibold">Deposit</button>
                    <button onClick={() => navigate('/withdraw', { state: { accountNumber: account?.accountNumber } })} className="py-2.5 text-gray-700 bg-white border border-gray-200 rounded hover:bg-gray-50 transition font-semibold">Withdraw</button>
                </div>
            </div>
        </Layout>
    );
};

export default Dashboard;
