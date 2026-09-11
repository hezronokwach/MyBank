import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';
import Layout from '../components/Layout';

const Dashboard = () => {
    const [accounts, setAccounts] = useState([]);
    const [selectedAccountIndex, setSelectedAccountIndex] = useState(0);
    const [isAdmin, setIsAdmin] = useState(false);
    const navigate = useNavigate();

    useEffect(() => {
        const fetchRole = async () => {
            try {
                const response = await api.get('/auth/me/role');
                if (response.data === 'ROLE_ADMIN') {
                    setIsAdmin(true);
                    navigate('/admin');
                }
            } catch (error) {
                console.error('Failed to fetch user role', error);
            }
        };
        fetchRole();
        
        const fetchAccounts = async () => {
            try {
                const response = await api.get('/accounts/me');
                setAccounts(response.data);
            } catch (error) {
                console.error('Failed to fetch accounts', error);
            }
        };
        fetchAccounts();
    }, [navigate]);
    
    const account = accounts.length > 0 ? accounts[selectedAccountIndex] : null;

    if (isAdmin) {
        return null; // Or some loading indicator while redirecting
    }

    return (
        <Layout>
            <div className="max-w-5xl mx-auto px-4 py-8">
                <div className="flex items-center justify-between mb-8">
                    <h1 className="text-3xl font-extrabold text-bank-heading tracking-tight">Your Dashboard</h1>
                    {accounts.length > 1 && (
                        <select 
                            className="bg-white border border-gray-300 rounded-md py-2 px-3 focus:outline-none focus:ring-2 focus:ring-[#183a62]"
                            value={selectedAccountIndex} 
                            onChange={(e) => setSelectedAccountIndex(Number(e.target.value))}
                        >
                            {accounts.map((acc, index) => (
                                <option key={acc.accountNumber} value={index}>
                                    {acc.accountNumber} ({acc.type})
                                </option>
                            ))}
                        </select>
                    )}
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
                    <button onClick={() => navigate('/transfer')} className="py-2.5 text-white bg-[#183a62] rounded hover:bg-[#102e50] transition font-semibold">Transfer</button>
                    <button onClick={() => navigate('/deposit', { state: { accountNumber: account?.accountNumber } })} className="py-2.5 text-[#183a62] bg-white border border-slate-300 rounded hover:bg-slate-50 transition font-semibold">Deposit</button>
                    <button onClick={() => navigate('/withdraw', { state: { accountNumber: account?.accountNumber } })} className="py-2.5 text-[#183a62] bg-white border border-slate-300 rounded hover:bg-slate-50 transition font-semibold">Withdraw</button>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mt-4">
                    <button onClick={() => navigate('/create-account')} className="py-2.5 text-white bg-green-700 rounded hover:bg-green-800 transition font-semibold">Create Account</button>
                    {account && (
                        <>
                            <button onClick={() => navigate('/transfer-ownership', { state: { accountNumber: account?.accountNumber } })} className="py-2.5 text-[#183a62] bg-white border border-slate-300 rounded hover:bg-slate-50 transition font-semibold">Transfer Ownership</button>
                            <button onClick={() => navigate('/delete-account', { state: { accountNumber: account?.accountNumber } })} className="py-2.5 text-white bg-red-700 rounded hover:bg-red-800 transition font-semibold">Delete Account</button>
                        </>
                    )}
                </div>
            </div>
        </Layout>
    );
};

export default Dashboard;
