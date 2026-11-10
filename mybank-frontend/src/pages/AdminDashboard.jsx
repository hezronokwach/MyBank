import { useState, useEffect } from 'react';
import api from '../api/axiosInstance';
import Layout from '../components/Layout';
import { useNotification } from '../components/notificationContext';

// The tiles at the top. Clicking one shows only the accounts with that status.
const FILTERS = [
    { key: 'PENDING_VERIFICATION', label: 'Waiting for approval' },
    { key: 'ACTIVE', label: 'Active' },
    { key: 'FROZEN', label: 'Frozen' },
    { key: 'ALL', label: 'All accounts' },
];

function describeStatus(status) {
    if (status === 'PENDING_VERIFICATION') return 'Waiting for approval';
    if (status === 'ACTIVE') return 'Active';
    if (status === 'FROZEN') return 'Frozen';
    if (status === 'CLOSED') return 'Closed';
    return status;
}

function badgeClass(status) {
    if (status === 'PENDING_VERIFICATION') return 'badge badge--pending';
    if (status === 'ACTIVE') return 'badge badge--active';
    if (status === 'FROZEN') return 'badge badge--frozen';
    return 'badge badge--closed';
}

function formatMoney(amount, currency) {
    return new Intl.NumberFormat('en-KE', { style: 'currency', currency: currency || 'KES', minimumFractionDigits: 2 }).format(amount || 0);
}

function formatDate(isoText) {
    if (!isoText) return '';
    return new Date(isoText).toLocaleDateString('en-KE', { day: 'numeric', month: 'short', year: 'numeric' });
}

// The admin's main page: approve new accounts, unfreeze frozen ones and change account types.
const AdminDashboard = () => {
    const [accounts, setAccounts] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadFailed, setLoadFailed] = useState(false);
    const [filter, setFilter] = useState('PENDING_VERIFICATION');
    const [busyAccount, setBusyAccount] = useState(null);
    const { notify } = useNotification();

    useEffect(() => {
        const fetchAccounts = async () => {
            try {
                const response = await api.get('/accounts');
                setAccounts(response.data);
            } catch (error) {
                console.error('Failed to fetch accounts', error);
                setLoadFailed(true);
            } finally {
                setIsLoading(false);
            }
        };
        fetchAccounts();
    }, []);

    // How many accounts have this status ("ALL" counts every account)
    const countFor = (key) => {
        if (key === 'ALL') return accounts.length;
        let count = 0;
        for (const account of accounts) {
            if (account.status === key) count = count + 1;
        }
        return count;
    };

    const visibleAccounts = [];
    for (const account of accounts) {
        if (filter === 'ALL' || account.status === filter) {
            visibleAccounts.push(account);
        }
    }

    // Puts the new status on one account in the list, so the page updates without reloading
    const updateStatusInList = (accountNumber, status) => {
        const updated = [];
        for (const account of accounts) {
            if (account.accountNumber === accountNumber) {
                updated.push({ ...account, status: status });
            } else {
                updated.push(account);
            }
        }
        setAccounts(updated);
    };

    const handleApprove = async (accountNumber) => {
        setBusyAccount(accountNumber);
        try {
            await api.patch(`/accounts/${accountNumber}/status`, { status: 'ACTIVE' });
            updateStatusInList(accountNumber, 'ACTIVE');
            notify(`Account ${accountNumber} is approved and active.`);
        } catch (error) {
            notify(error.response?.data?.message || 'Could not approve the account.', 'error');
        } finally {
            setBusyAccount(null);
        }
    };

    const handleUnfreeze = async (accountNumber) => {
        if (!window.confirm(`Unfreeze account ${accountNumber}? The customer will be able to use it again.`)) {
            return;
        }
        setBusyAccount(accountNumber);
        try {
            await api.patch(`/accounts/${accountNumber}/status`, { status: 'ACTIVE' });
            updateStatusInList(accountNumber, 'ACTIVE');
            notify(`Account ${accountNumber} is unfrozen.`);
        } catch (error) {
            notify(error.response?.data?.message || 'Could not unfreeze the account.', 'error');
        } finally {
            setBusyAccount(null);
        }
    };

    const handleUpdateType = async (accountNumber, type) => {
        try {
            await api.patch(`/accounts/${accountNumber}/type`, type, {
                headers: { 'Content-Type': 'application/json' }
            });
            const updated = [];
            for (const account of accounts) {
                if (account.accountNumber === accountNumber) {
                    updated.push({ ...account, type: type });
                } else {
                    updated.push(account);
                }
            }
            setAccounts(updated);
            notify(`Account ${accountNumber} is now a ${type.toLowerCase()} account.`);
        } catch (error) {
            notify(error.response?.data?.message || 'Could not change the account type.', 'error');
        }
    };

    let emptyText = 'No accounts here.';
    if (filter === 'PENDING_VERIFICATION') {
        emptyText = 'Nothing to approve. New accounts will appear here.';
    }

    return (
        <Layout access="admin">
            <header className="page-header">
                <div>
                    <p className="eyebrow">Admin</p>
                    <h1>Account approvals</h1>
                    <p>Approve new accounts, unfreeze accounts and change account types.</p>
                </div>
            </header>

            <div className="summary-tiles">
                {FILTERS.map((item) => {
                    let className = 'summary-tile';
                    if (item.key === 'PENDING_VERIFICATION') className = className + ' summary-tile--pending';
                    if (item.key === filter) className = className + ' summary-tile--active';
                    return (
                        <button key={item.key} type="button" className={className} aria-pressed={item.key === filter} onClick={() => setFilter(item.key)}>
                            <span>{item.label}</span>
                            <strong>{countFor(item.key)}</strong>
                        </button>
                    );
                })}
            </div>

            <section className="card">
                {isLoading && <div className="activity-empty"><p>Loading accounts…</p></div>}

                {!isLoading && loadFailed && (
                    <div className="activity-empty activity-error"><span>!</span><p>Couldn't load the accounts</p><small>Refresh the page to try again.</small></div>
                )}

                {!isLoading && !loadFailed && visibleAccounts.length === 0 && (
                    <div className="activity-empty"><span>✓</span><p>{emptyText}</p></div>
                )}

                {!isLoading && !loadFailed && visibleAccounts.length > 0 && (
                    <div className="table-scroll">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>Account</th>
                                    <th>Type</th>
                                    <th>Balance</th>
                                    <th>Status</th>
                                    <th className="data-table__actions">Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {visibleAccounts.map((account) => (
                                    <tr key={account.accountNumber}>
                                        <td data-label="Account">
                                            <span className="data-table__number">{account.accountNumber}</span>
                                            <span className="data-table__sub">Opened {formatDate(account.createdAt)}</span>
                                        </td>
                                        <td data-label="Type">
                                            <select
                                                className="table-select"
                                                value={account.type}
                                                onChange={(e) => handleUpdateType(account.accountNumber, e.target.value)}
                                                aria-label={`Type of account ${account.accountNumber}`}
                                            >
                                                <option value="CURRENT">Current</option>
                                                <option value="SAVINGS">Savings</option>
                                                <option value="FIXED_01">Fixed 01</option>
                                                <option value="FIXED_02">Fixed 02</option>
                                                <option value="FIXED_03">Fixed 03</option>
                                            </select>
                                        </td>
                                        <td data-label="Balance">{formatMoney(account.balance, account.currency)}</td>
                                        <td data-label="Status"><span className={badgeClass(account.status)}>{describeStatus(account.status)}</span></td>
                                        <td className="data-table__actions">
                                            {account.status === 'PENDING_VERIFICATION' && (
                                                <button type="button" className="button button--primary button--small" disabled={busyAccount === account.accountNumber} onClick={() => handleApprove(account.accountNumber)}>
                                                    {busyAccount === account.accountNumber ? 'Approving…' : 'Approve'}
                                                </button>
                                            )}
                                            {account.status === 'FROZEN' && (
                                                <button type="button" className="button button--secondary button--small" disabled={busyAccount === account.accountNumber} onClick={() => handleUnfreeze(account.accountNumber)}>
                                                    {busyAccount === account.accountNumber ? 'Unfreezing…' : 'Unfreeze'}
                                                </button>
                                            )}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                )}
            </section>
        </Layout>
    );
};

export default AdminDashboard;
