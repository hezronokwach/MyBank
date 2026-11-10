import { useState, useEffect } from 'react';
import api from '../api/axiosInstance';
import Layout from '../components/Layout';
import { useNotification } from '../components/notificationContext';

// The tiles at the top. Clicking one shows only the cards with that status.
const FILTERS = [
    { key: 'PENDING_APPROVAL', label: 'Waiting for approval' },
    { key: 'ACTIVE', label: 'Issued' },
    { key: 'REJECTED', label: 'Rejected' },
    { key: 'ALL', label: 'All cards' },
];

function describeStatus(status) {
    if (status === 'PENDING_APPROVAL') return 'Waiting for approval';
    if (status === 'ACTIVE') return 'Issued';
    if (status === 'REJECTED') return 'Rejected';
    if (status === 'FROZEN') return 'Frozen';
    return status;
}

function badgeClass(status) {
    if (status === 'PENDING_APPROVAL') return 'badge badge--pending';
    if (status === 'ACTIVE') return 'badge badge--active';
    if (status === 'FROZEN') return 'badge badge--frozen';
    return 'badge badge--closed';
}

function formatDate(isoText) {
    if (!isoText) return '';
    return new Date(isoText).toLocaleDateString('en-KE', { day: 'numeric', month: 'short', year: 'numeric' });
}

// "3" and "2029" become "03/29"
function formatExpiry(month, year) {
    if (!month || !year) return '';
    let monthText = String(month);
    if (month < 10) monthText = '0' + month;
    return monthText + '/' + String(year).slice(-2);
}

// /admin/cards: approve a card (it gets its number) or reject it (the fee goes back to the customer)
const AdminCardsPage = () => {
    const [cards, setCards] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadFailed, setLoadFailed] = useState(false);
    const [filter, setFilter] = useState('PENDING_APPROVAL');
    const [busyCard, setBusyCard] = useState(null);
    const { notify } = useNotification();

    useEffect(() => {
        const fetchCards = async () => {
            try {
                const response = await api.get('/admin/cards');
                setCards(response.data);
            } catch (error) {
                console.error('Failed to fetch cards', error);
                setLoadFailed(true);
            } finally {
                setIsLoading(false);
            }
        };
        fetchCards();
    }, []);

    const countFor = (key) => {
        if (key === 'ALL') return cards.length;
        let count = 0;
        for (const card of cards) {
            if (card.status === key) count = count + 1;
        }
        return count;
    };

    const visibleCards = [];
    for (const card of cards) {
        if (filter === 'ALL' || card.status === filter) {
            visibleCards.push(card);
        }
    }

    // Puts the updated card in the list, so the page changes without reloading
    const replaceCard = (updatedCard) => {
        const updated = [];
        for (const card of cards) {
            if (card.id === updatedCard.id) {
                updated.push(updatedCard);
            } else {
                updated.push(card);
            }
        }
        setCards(updated);
    };

    const handleApprove = async (card) => {
        setBusyCard(card.id);
        try {
            const response = await api.post(`/admin/cards/${card.id}/approve`);
            replaceCard(response.data);
            notify(`Card issued to ${card.customerName}. It ends in ${response.data.last4}.`);
        } catch (error) {
            notify(error.response?.data?.message || 'Could not approve the card.', 'error');
        } finally {
            setBusyCard(null);
        }
    };

    const handleReject = async (card) => {
        const reason = window.prompt(`Why are you rejecting ${card.customerName}'s card? They will see this, and the KES ${card.feePaid} fee goes back to them.`);
        if (reason === null) {
            return; // the admin pressed Cancel
        }
        if (reason.trim() === '') {
            notify('Please give a reason.', 'error');
            return;
        }
        setBusyCard(card.id);
        try {
            const response = await api.post(`/admin/cards/${card.id}/reject`, { reason: reason });
            replaceCard(response.data);
            notify(`Card rejected. KES ${card.feePaid} was refunded to ${card.customerName}.`);
        } catch (error) {
            notify(error.response?.data?.message || 'Could not reject the card.', 'error');
        } finally {
            setBusyCard(null);
        }
    };

    let emptyText = 'No cards here.';
    if (filter === 'PENDING_APPROVAL') {
        emptyText = 'No card applications waiting. New ones will appear here.';
    }

    return (
        <Layout access="admin">
            <header className="page-header">
                <div>
                    <p className="eyebrow">Admin</p>
                    <h1>Card applications</h1>
                    <p>Approve a card to issue it, or reject it and the fee goes back to the customer.</p>
                </div>
            </header>

            <div className="summary-tiles">
                {FILTERS.map((item) => {
                    let className = 'summary-tile';
                    if (item.key === 'PENDING_APPROVAL') className = className + ' summary-tile--pending';
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
                {isLoading && <div className="activity-empty"><p>Loading cards…</p></div>}

                {!isLoading && loadFailed && (
                    <div className="activity-empty activity-error"><span>!</span><p>Couldn't load the cards</p><small>Refresh the page to try again.</small></div>
                )}

                {!isLoading && !loadFailed && visibleCards.length === 0 && (
                    <div className="activity-empty"><span>✓</span><p>{emptyText}</p></div>
                )}

                {!isLoading && !loadFailed && visibleCards.length > 0 && (
                    <div className="table-scroll">
                        <table className="data-table">
                            <thead>
                                <tr>
                                    <th>Customer</th>
                                    <th>Pays from</th>
                                    <th>Fee</th>
                                    <th>Status</th>
                                    <th className="data-table__actions">Action</th>
                                </tr>
                            </thead>
                            <tbody>
                                {visibleCards.map((card) => (
                                    <tr key={card.id}>
                                        <td data-label="Customer">
                                            <span className="data-table__number">{card.customerName}</span>
                                            <span className="data-table__sub">{card.customerEmail} · applied {formatDate(card.appliedAt)}</span>
                                        </td>
                                        <td data-label="Pays from">{card.linkedAccountNumber}</td>
                                        <td data-label="Fee">KES {card.feePaid}</td>
                                        <td data-label="Status">
                                            <span className={badgeClass(card.status)}>{describeStatus(card.status)}</span>
                                            {card.last4 && <span className="data-table__sub">•••• {card.last4} · {formatExpiry(card.expiryMonth, card.expiryYear)}</span>}
                                            {card.rejectionReason && <span className="data-table__sub">{card.rejectionReason}</span>}
                                        </td>
                                        <td className="data-table__actions">
                                            {card.status === 'PENDING_APPROVAL' && (
                                                <>
                                                    <button type="button" className="button button--primary button--small" disabled={busyCard === card.id} onClick={() => handleApprove(card)}>
                                                        {busyCard === card.id ? 'Working…' : 'Approve'}
                                                    </button>{' '}
                                                    <button type="button" className="button button--secondary button--small" disabled={busyCard === card.id} onClick={() => handleReject(card)}>
                                                        Reject
                                                    </button>
                                                </>
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

export default AdminCardsPage;
