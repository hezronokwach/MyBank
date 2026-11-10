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
    const [firstName, setFirstName] = useState('');
    // Goes up by one whenever the accounts should be loaded again (for example after the assistant froze one)
    const [accountsVersion, setAccountsVersion] = useState(0);
    const navigate = useNavigate();


    // The customer's first name, for the greeting
    useEffect(() => {
        const fetchName = async () => {
            try {
                const response = await api.get('/auth/me');
                setFirstName(response.data.firstName || '');
            } catch (error) {
                console.error('Failed to fetch your name', error);
            }
        };
        fetchName();
    }, []);

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
        if (transaction.type === 'CARD_FEE') return 'Card fee';
        if (transaction.type === 'CARD_FEE_REFUND') return 'Card fee refund';
        return transaction.direction === 'IN'
            ? `Received from account •••${transaction.counterpartyLast4 || ''}`
            : `Sent to account •••${transaction.counterpartyLast4 || ''}`;
    };

    const describeStatus = (status) => {
        if (status === 'ACTIVE') return 'Active';
        if (status === 'FROZEN') return 'Frozen';
        if (status === 'CLOSED') return 'Closed';
        return 'Waiting for approval';
    };

    const formatAccount = (currentAccount) => `•••${currentAccount.accountNumber.slice(-4)} · ${currentAccount.type.charAt(0) + currentAccount.type.slice(1).toLowerCase()}`;

    const toggleBalanceVisibility = () => {
        setIsBalanceHidden((hidden) => {
            localStorage.setItem('hideBalance', String(!hidden));
            return !hidden;
        });
    };

    let greeting = 'Good to see you.';
    if (firstName) {
        greeting = 'Good to see you, ' + firstName + '.';
    }

    return (
        <Layout access="customer">
            <div className="dashboard-shell">
                <header className="dashboard-heading">
                    <div><p className="eyebrow">My money</p><h1>{greeting}</h1></div>
                    {accounts.length > 1 && (
                        <div className="account-chips" role="group" aria-label="Choose account">
                            {accounts.map((acc, index) => (
                                <button
                                    key={acc.accountNumber}
                                    type="button"
                                    className={index === selectedAccountIndex ? 'account-chip account-chip--active' : 'account-chip'}
                                    aria-pressed={index === selectedAccountIndex}
                                    onClick={() => setSelectedAccountIndex(index)}
                                >
                                    {formatAccount(acc)}
                                </button>
                            ))}
                        </div>
                    )}
                </header>
                {isLoading ? (
                    <div className="dashboard-loading">Loading your account…</div>
                ) : account ? (
                    <>
                        <section className="balance-card">
                            <div className="balance-card__top"><span>{account.type.charAt(0) + account.type.slice(1).toLowerCase()} account</span><span className="balance-card__status">{describeStatus(account.status)}</span></div>
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
                        <section className="card manage-card">
                            <div className="section-heading"><div><h2>Manage this account</h2><p>Account •••{account.accountNumber.slice(-4)}</p></div></div>
                            <div className="manage-options">
                                <button type="button" className="manage-option" onClick={() => navigate('/create-account')}>
                                    <span className="manage-option__icon" aria-hidden="true">+</span>
                                    <strong>Open another account</strong>
                                    <span>Savings, current or fixed deposit</span>
                                </button>
                                <button type="button" className="manage-option" onClick={() => navigate('/transfer-ownership', { state: { accountNumber: account.accountNumber } })}>
                                    <span className="manage-option__icon" aria-hidden="true">⇄</span>
                                    <strong>Transfer ownership</strong>
                                    <span>Give this account to another customer</span>
                                </button>
                                <button type="button" className="manage-option manage-option--danger" onClick={() => navigate('/delete-account', { state: { accountNumber: account.accountNumber } })}>
                                    <span className="manage-option__icon" aria-hidden="true">×</span>
                                    <strong>Close this account</strong>
                                    <span>This can't be undone</span>
                                </button>
                            </div>
                        </section>
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
