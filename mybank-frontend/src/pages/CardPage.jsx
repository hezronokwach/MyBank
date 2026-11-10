import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import api from '../api/axiosInstance';
import Layout from '../components/Layout';
import ApplyForCard from '../components/card/ApplyForCard';
import VirtualCard from '../components/card/VirtualCard';
import MoveMoneyForm from '../components/card/MoveMoneyForm';
import CardHistory from '../components/card/CardHistory';
import { formatMoney } from '../components/card/cardHelpers';

// /card: everything about the customer's virtual card.
//   no card (or the last one was rejected): apply
//   waiting for approval: a message
//   issued: the card, show details, add money, move to account, card activity
const CardPage = () => {
    const [cards, setCards] = useState([]);
    const [accounts, setAccounts] = useState([]);
    const [fee, setFee] = useState(null);
    const [ownerName, setOwnerName] = useState('');
    const [isLoading, setIsLoading] = useState(true);
    const [loadFailed, setLoadFailed] = useState(false);
    const [tab, setTab] = useState('topUp');
    // Goes up after every change, which loads the cards, accounts and history again
    const [refreshCount, setRefreshCount] = useState(0);

    useEffect(() => {
        const fetchEverything = async () => {
            try {
                const cardsResponse = await api.get('/cards/me');
                const accountsResponse = await api.get('/accounts/me');
                const feeResponse = await api.get('/cards/fee');
                const meResponse = await api.get('/auth/me');
                setCards(cardsResponse.data);
                setAccounts(accountsResponse.data);
                setFee(feeResponse.data);
                setOwnerName(meResponse.data.firstName + ' ' + meResponse.data.lastName);
                setLoadFailed(false);
            } catch (error) {
                console.error('Failed to load the card page', error);
                setLoadFailed(true);
            } finally {
                setIsLoading(false);
            }
        };
        fetchEverything();
    }, [refreshCount]);

    const reload = () => {
        setRefreshCount(refreshCount + 1);
    };

    // The newest card decides what the page shows
    let latest = null;
    if (cards.length > 0) {
        latest = cards[0];
    }

    let content;
    if (isLoading) {
        content = <div className="dashboard-loading">Loading your card…</div>;
    } else if (loadFailed) {
        content = <div className="activity-empty activity-error"><span>!</span><p>Couldn't load your card</p><small>Refresh the page to try again.</small></div>;
    } else if (latest === null || latest.status === 'REJECTED') {
        let rejected = null;
        if (latest !== null) {
            rejected = latest;
        }
        content = <ApplyForCard accounts={accounts} fee={fee} rejectedCard={rejected} onApplied={reload} />;
    } else if (latest.status === 'PENDING_APPROVAL') {
        content = (
            <section className="card card-panel">
                <p className="eyebrow">Virtual card</p>
                <h2 className="card-panel__title">Waiting for approval</h2>
                <p className="card-panel__text">
                    We received your application. The {formatMoney(latest.feePaid, latest.currency)} fee was taken from {latest.linkedAccount}.
                    An admin will check it soon; if it is not approved, the fee comes back to you.
                </p>
                <span className="badge badge--pending">Waiting for approval</span>
            </section>
        );
    } else {
        // ACTIVE or FROZEN
        content = (
            <>
                <div className="card-page-grid">
                    <section className="card card-panel">
                        <VirtualCard card={latest} ownerName={ownerName} />
                    </section>

                    <section className="card card-panel">
                        {latest.status === 'FROZEN' && (
                            <p className="card-notice card-notice--warning">This card is frozen, so money can't be moved. Contact support.</p>
                        )}
                        {latest.status === 'ACTIVE' && (
                            <>
                                <div className="segmented" role="tablist" aria-label="Move money">
                                    <button type="button" role="tab" aria-selected={tab === 'topUp'}
                                        className={tab === 'topUp' ? 'segmented__option segmented__option--active' : 'segmented__option'}
                                        onClick={() => setTab('topUp')}>Add money</button>
                                    <button type="button" role="tab" aria-selected={tab === 'withdraw'}
                                        className={tab === 'withdraw' ? 'segmented__option segmented__option--active' : 'segmented__option'}
                                        onClick={() => setTab('withdraw')}>Move to account</button>
                                </div>
                                {tab === 'topUp' && <MoveMoneyForm key="topUp" card={latest} accounts={accounts} direction="topUp" onMoved={reload} />}
                                {tab === 'withdraw' && <MoveMoneyForm key="withdraw" card={latest} accounts={accounts} direction="withdraw" onMoved={reload} />}
                            </>
                        )}
                    </section>
                </div>
                <CardHistory card={latest} refreshCount={refreshCount} />
            </>
        );
    }

    return (
        <Layout access="customer">
            <div className="dashboard-shell card-page">
                <Link to="/dashboard" className="back-link">← Back to dashboard</Link>
                <header className="page-header">
                    <div>
                        <p className="eyebrow">My card</p>
                        <h1>Your MyBank card</h1>
                    </div>
                </header>
                {content}
            </div>
        </Layout>
    );
};

export default CardPage;
