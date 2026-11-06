import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/axiosInstance';
import Layout from '../components/Layout';
import ChatPanel from '../components/assistant/ChatPanel';

const Dashboard = () => {
    const [accounts, setAccounts] = useState([]);
    const [transactions, setTransactions] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [isActivityLoading, setIsActivityLoading] = useState(false);
    const [activityError, setActivityError] = useState(false);
    const [isBalanceHidden, setIsBalanceHidden] = useState(() => localStorage.getItem('hideBalance') === 'true');
    const [selectedAccountIndex, setSelectedAccountIndex] = useState(0);
    const [isAdmin, setIsAdmin] = useState(false);
    // Goes up by one whenever the accounts should be loaded again (for example after the assistant froze one)
    const [accountsVersion, setAccountsVersion] = useState(0);
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
    }, [navigate]);

    useEffect(() => {
        const fetchAccounts = async () => {
            try {
                const response = await api.get('/accounts/me');
                setAccounts(response.data);
            } catch (error) {
                console.error('Failed to fetch accounts', error);
            } finally {
                setIsLoading(false);
            }
        };
        fetchAccounts();
    }, [accountsVersion]);
    
    const account = accounts.length > 0 ? accounts[selectedAccountIndex] : null;

    useEffect(() => {
        const fetchTransactions = async () => {
            if (!account?.accountNumber) {
                setTransactions([]);
                setActivityError(false);
                return;
            }
            setIsActivityLoading(true);
            setActivityError(false);
            try {
                const response = await api.get(`/accounts/${account.accountNumber}/transactions?limit=5`);
                setTransactions(response.data);
            } catch (error) {
                console.error('Failed to fetch recent transactions', error);
                setTransactions([]);
                setActivityError(true);
            } finally {
                setIsActivityLoading(false);
            }
        };
        fetchTransactions();
    }, [account?.accountNumber, accountsVersion]);

    const formatMoney = (amount, currency) => new Intl.NumberFormat('en-KE', {
        style: 'currency', currency: currency || 'KES', minimumFractionDigits: 2,
    }).format(amount);

    const describeTransaction = (transaction) => {
        if (transaction.type === 'DEPOSIT') return 'Money added';
        if (transaction.type === 'WITHDRAWAL') return 'Cash withdrawal';
        return transaction.direction === 'IN'
            ? `Received from account •••${transaction.counterpartyLast4 || ''}`
            : `Sent to account •••${transaction.counterpartyLast4 || ''}`;
    };

    const formatAccount = (currentAccount) => `•••${currentAccount.accountNumber.slice(-4)} · ${currentAccount.type.charAt(0) + currentAccount.type.slice(1).toLowerCase()}`;

    const toggleBalanceVisibility = () => {
        setIsBalanceHidden((hidden) => {
            localStorage.setItem('hideBalance', String(!hidden));
            return !hidden;
        });
    };

    if (isAdmin) {
        return null; // Or some loading indicator while redirecting
    }

    return (
        <Layout>
            <div className="dashboard-shell">
                <header className="dashboard-heading">
                    <div><p className="eyebrow">My money</p><h1>Good to see you.</h1></div>
                    {accounts.length > 1 && (
                        <select 
                            className="dashboard-account-switcher"
                            value={selectedAccountIndex} 
                            onChange={(e) => setSelectedAccountIndex(Number(e.target.value))}
                            aria-label="Choose account"
                        >
                            {accounts.map((acc, index) => (
                                <option key={acc.accountNumber} value={index}>
                                    {formatAccount(acc)}
                                </option>
                            ))}
                        </select>
                    )}
                </header>
                {isLoading ? (
                    <div className="dashboard-loading">Loading your account…</div>
                ) : account ? (
                    <>
                        <section className="balance-card">
                            <div className="balance-card__top"><span>{account.type.charAt(0) + account.type.slice(1).toLowerCase()} account</span><span className="balance-card__status">{account.status === 'ACTIVE' ? 'Active' : 'Pending'}</span></div>
                            <div className="balance-card__balance-heading"><p className="balance-card__label">Available balance</p><button type="button" onClick={toggleBalanceVisibility} aria-pressed={isBalanceHidden} aria-label={isBalanceHidden ? 'Show balance' : 'Hide balance'}>{isBalanceHidden ? 'Show' : 'Hide'}</button></div>
                            <p className="balance-card__amount">{isBalanceHidden ? `${account.currency} ••••••` : formatMoney(account.balance, account.currency)}</p>
                            <div className="balance-card__footer"><span>Account •••{account.accountNumber.slice(-4)}</span><span>{account.currency}</span></div>
                        </section>

                        <section className="quick-actions" aria-label="Quick actions">
                            <button onClick={() => navigate('/deposit', { state: { accountNumber: account.accountNumber } })}><span className="quick-actions__icon quick-actions__icon--add">↓</span><span>Add money</span></button>
                            <button onClick={() => navigate('/transfer')}><span className="quick-actions__icon quick-actions__icon--send">↗</span><span>Send money</span></button>
                            <button onClick={() => navigate('/withdraw', { state: { accountNumber: account.accountNumber } })}><span className="quick-actions__icon quick-actions__icon--withdraw">↑</span><span>Withdraw</span></button>
                        </section>

                        <section className="activity-card">
                            <div className="section-heading"><div><h2>Recent activity</h2><p>Your latest money movement</p></div><span>Last 5</span></div>
                            {isActivityLoading ? <div className="activity-empty"><p>Loading recent activity…</p></div> : activityError ? <div className="activity-empty activity-error"><p>Couldn’t load recent activity</p><small>Refresh the page to try again.</small></div> : transactions.length ? <div className="transaction-list">
                                {transactions.map((transaction) => (
                                    <article className="transaction-row" key={transaction.reference}>
                                        <span className={`transaction-row__icon transaction-row__icon--${transaction.direction === 'IN' ? 'in' : 'out'}`}>{transaction.direction === 'IN' ? '↓' : '↑'}</span>
                                        <div><h3>{describeTransaction(transaction)}</h3><p>{new Date(transaction.date).toLocaleDateString('en-KE', { day: 'numeric', month: 'short' })} · {transaction.status.toLowerCase()}</p></div>
                                        <strong className={transaction.direction === 'IN' ? 'amount-in' : 'amount-out'}>{transaction.direction === 'IN' ? '+' : '−'}{formatMoney(transaction.amount, transaction.currency)}</strong>
                                    </article>
                                ))}
                            </div> : <div className="activity-empty"><span>⌁</span><p>No transactions yet</p><small>Your deposits, withdrawals, and transfers will appear here.</small></div>}
                        </section>
                        <details className="account-options">
                            <summary>Account options</summary>
                            <div>
                                <button onClick={() => navigate('/create-account')}>Create another account</button>
                                <button onClick={() => navigate('/transfer-ownership', { state: { accountNumber: account.accountNumber } })}>Transfer ownership</button>
                                <button className="account-options__danger" onClick={() => navigate('/delete-account', { state: { accountNumber: account.accountNumber } })}>Close this account</button>
                            </div>
                        </details>
                    </>
                ) : (
                    <div className="activity-empty dashboard-empty">
                        <span>⌁</span><p>You do not have an account yet.</p><small>Create one to start managing your money.</small>
                        <button onClick={() => navigate('/create-account')} className="button button--primary">Create account</button>
                    </div>
                )}
                <ChatPanel onAccountsChanged={() => setAccountsVersion((version) => version + 1)} />
            </div>
        </Layout>
    );
};

export default Dashboard;
