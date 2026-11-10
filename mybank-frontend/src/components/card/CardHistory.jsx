import { useState, useEffect } from 'react';
import api from '../../api/axiosInstance';
import { formatMoney } from './cardHelpers';

// The card's last 10 moves. refreshCount goes up after every move, which loads the list again.
const CardHistory = ({ card, refreshCount }) => {
    const [moves, setMoves] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [loadFailed, setLoadFailed] = useState(false);

    useEffect(() => {
        const fetchMoves = async () => {
            try {
                const response = await api.get(`/cards/${card.id}/transactions?limit=10`);
                setMoves(response.data);
                setLoadFailed(false);
            } catch (error) {
                console.error('Failed to fetch card history', error);
                setLoadFailed(true);
            } finally {
                setIsLoading(false);
            }
        };
        fetchMoves();
    }, [card.id, refreshCount]);

    let content;
    if (isLoading) {
        content = <div className="activity-empty"><p>Loading…</p></div>;
    } else if (loadFailed) {
        content = <div className="activity-empty activity-error"><span>!</span><p>Couldn't load the card history</p></div>;
    } else if (moves.length === 0) {
        content = <div className="activity-empty"><span>⌁</span><p>Nothing yet</p><small>Money you add to the card or move back will show here.</small></div>;
    } else {
        content = (
            <div className="transaction-list">
                {moves.map((move) => {
                    const isIn = move.direction === 'IN';
                    let title = 'Moved to account ' + move.account;
                    if (isIn) {
                        title = 'Added from account ' + move.account;
                    }
                    return (
                        <article className="transaction-row" key={move.reference}>
                            <span className={isIn ? 'transaction-row__icon transaction-row__icon--in' : 'transaction-row__icon transaction-row__icon--out'}>{isIn ? '↓' : '↑'}</span>
                            <div>
                                <h3>{title}</h3>
                                <p>{new Date(move.date).toLocaleDateString('en-KE', { day: 'numeric', month: 'short' })} · {move.reference}</p>
                            </div>
                            <strong className={isIn ? 'amount-in' : 'amount-out'}>{isIn ? '+' : '−'}{formatMoney(move.amount, card.currency)}</strong>
                        </article>
                    );
                })}
            </div>
        );
    }

    return (
        <section className="activity-card card-history">
            <div className="section-heading"><div><h2>Card activity</h2><p>Money onto and off your card</p></div><span>Last 10</span></div>
            {content}
        </section>
    );
};

export default CardHistory;
