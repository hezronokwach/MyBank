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
                    <div className="bg-gradient-to-br from-indigo-600 to-violet-700 rounded-3xl shadow-lg shadow-indigo-200 overflow-hidden text-white">
                        <div className="p-8 border-b border-white/10">
                            <p className="text-sm text-indigo-100 uppercase tracking-wider font-bold">Total Balance</p>
                            <p className="text-5xl font-extrabold mt-2">{account.balance} <span className="text-2xl text-indigo-200">{account.currency}</span></p>
                        </div>
                        
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-0 divide-y md:divide-y-0 md:divide-x divide-white/10 bg-white/5">
                            <div className="p-6">
                                <p className="text-xs text-indigo-200 uppercase tracking-widest font-semibold mb-1">Account Number</p>
                                <p className="font-bold text-white">{account.accountNumber}</p>
                            </div>
                            <div className="p-6">
                                <p className="text-xs text-indigo-200 uppercase tracking-widest font-semibold mb-1">Account Type</p>
                                <p className="font-bold text-white capitalize">{account.type.toLowerCase()}</p>
                            </div>
                            <div className="p-6">
                                <p className="text-xs text-indigo-200 uppercase tracking-widest font-semibold mb-1">Status</p>
                                <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-bold ${account.status === 'ACTIVE' ? 'bg-green-500/20 text-green-100 border border-green-500/30' : 'bg-amber-500/20 text-amber-100 border border-amber-500/30'}`}>
                                    {account.status}
                                </span>
                            </div>
                        </div>
                    </div>
                ) : (
                    <div className="text-center py-20 bg-white rounded-3xl border border-gray-100">
                        <p className="text-gray-500">No active accounts found.</p>
                    </div>
                )}
                
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-8">
                    <button onClick={() => navigate('/transfer')} className="py-4 text-white bg-indigo-600 rounded-2xl hover:bg-indigo-700 transition font-bold shadow-lg shadow-indigo-200">Transfer Funds</button>
                    <button onClick={() => navigate('/deposit', { state: { accountNumber: account?.accountNumber } })} className="py-4 text-gray-700 bg-white border border-gray-200 rounded-2xl hover:bg-gray-50 transition font-bold">Deposit</button>
                    <button onClick={() => navigate('/withdraw', { state: { accountNumber: account?.accountNumber } })} className="py-4 text-gray-700 bg-white border border-gray-200 rounded-2xl hover:bg-gray-50 transition font-bold">Withdraw</button>
                </div>
            </div>
        </Layout>
    );
};

export default Dashboard;
