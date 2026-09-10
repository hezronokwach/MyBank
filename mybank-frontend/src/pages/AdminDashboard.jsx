import { useState, useEffect } from 'react';
import api from '../api/axiosInstance';
import Layout from '../components/Layout';

const AdminDashboard = () => {
    const [accounts, setAccounts] = useState([]);

    useEffect(() => {
        const fetchAccounts = async () => {
            try {
                const response = await api.get('/accounts');
                setAccounts(response.data);
            } catch (error) {
                console.error('Failed to fetch accounts', error);
                alert('Access denied or failed to fetch accounts');
            }
        };
        fetchAccounts();
    }, []);

    const handleVerify = async (accountNumber) => {
        try {
            await api.patch(`/accounts/${accountNumber}/status`, { status: 'ACTIVE' });
            setAccounts(accounts.map(acc => acc.accountNumber === accountNumber ? { ...acc, status: 'ACTIVE' } : acc));
        } catch (error) {
            console.error('Failed to verify account', error);
            alert('Failed to verify account');
        }
    };

    const handleUpdateType = async (accountNumber, type) => {
        try {
            await api.patch(`/accounts/${accountNumber}/type`, type, {
                headers: { 'Content-Type': 'application/json' }
            });
            setAccounts(accounts.map(acc => acc.accountNumber === accountNumber ? { ...acc, type: type } : acc));
        } catch (error) {
            console.error('Failed to update account type', error);
            alert('Failed to update account type');
        }
    };

    return (
        <Layout>
            <div className="max-w-6xl mx-auto px-4 py-8">
                <div className="flex items-center justify-between mb-8">
                    <div>
                        <h1 className="text-3xl font-extrabold text-bank-heading tracking-tight">Admin Panel</h1>
                        <p className="text-gray-500 mt-1">Manage user accounts and verify statuses.</p>
                    </div>
                    <div className="flex items-center gap-3">
                        <span className="px-4 py-2 text-sm font-semibold text-indigo-700 bg-indigo-50 rounded-full border border-indigo-100">Total: {accounts.length} Accounts</span>
                    </div>
                </div>
                
                <div className="bg-white shadow-sm border border-gray-100 rounded-3xl overflow-hidden">
                    <table className="w-full text-left">
                        <thead className="bg-gray-50 border-b border-gray-100">
                            <tr>
                                <th className="px-8 py-5 font-bold text-gray-500 uppercase tracking-wider text-xs">Account Details</th>
                                <th className="px-8 py-5 font-bold text-gray-500 uppercase tracking-wider text-xs">Status</th>
                                <th className="px-8 py-5 font-bold text-gray-500 uppercase tracking-wider text-xs">Type</th>
                                <th className="px-8 py-5 font-bold text-gray-500 uppercase tracking-wider text-xs text-right">Actions</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {accounts.map(account => (
                                <tr key={account.accountNumber} className="hover:bg-gray-50/50 transition">
                                    <td className="px-8 py-5">
                                        <p className="font-bold text-bank-text">{account.accountNumber}</p>
                                    </td>
                                    <td className="px-8 py-5">
                                        <span className={`inline-flex items-center px-3 py-1 rounded-full text-xs font-bold ${account.status === 'ACTIVE' ? 'bg-green-100 text-green-700' : 'bg-amber-100 text-amber-700'}`}>
                                            {account.status}
                                        </span>
                                    </td>
                                    <td className="px-8 py-5">
                                        <select onChange={(e) => handleUpdateType(account.accountNumber, e.target.value)} defaultValue={account.type} className="text-sm border-gray-200 rounded-lg p-2">
                                            <option value="CURRENT">Current</option>
                                            <option value="SAVINGS">Savings</option>
                                        </select>
                                    </td>
                                    <td className="px-8 py-5 text-right">
                                        {account.status !== 'ACTIVE' && (
                                            <button 
                                                onClick={() => handleVerify(account.accountNumber)} 
                                                className="px-5 py-2.5 text-sm text-indigo-700 bg-indigo-50 rounded-xl hover:bg-indigo-100 transition font-bold"
                                            >
                                                Verify Account
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>
        </Layout>
    );
};

export default AdminDashboard;
