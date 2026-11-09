import { useState, useEffect } from 'react';
import api from '../../api/axiosInstance';

// "1234567" becomes "1,234,567"
function formatNumber(value) {
    return Number(value || 0).toLocaleString();
}

// Small amounts need more decimals: 0.00035 would show as $0.00 otherwise
function formatCost(value) {
    const amount = Number(value || 0);
    if (amount < 0.01) {
        return '$' + amount.toFixed(5);
    }
    return '$' + amount.toFixed(2);
}

function formatDate(isoText) {
    if (!isoText) {
        return '';
    }
    return new Date(isoText).toLocaleString();
}

// One box with a big number, a label and an optional line underneath
function MetricCard({ label, value, note }) {
    return (
        <div className="metric-card">
            <p className="metric-card__label">{label}</p>
            <p className="metric-card__value">{value}</p>
            {note && <p className="metric-card__note">{note}</p>}
        </div>
    );
}

// A group of cards with a heading
function MetricGroup({ title, children }) {
    return (
        <section className="metric-group">
            <h2>{title}</h2>
            <div className="metric-grid">
                {children}
            </div>
        </section>
    );
}

// The assistant's numbers for admins. They come from GET /api/v1/admin/assistant/metrics
// and start again from 0 every time the backend restarts.
const AssistantMetricsPanel = () => {
    const [metrics, setMetrics] = useState(null);
    const [isLoading, setIsLoading] = useState(true);
    const [hasError, setHasError] = useState(false);

    // Goes up by one each time Refresh is pressed, which loads the numbers again
    const [refreshCount, setRefreshCount] = useState(0);

    // Loads the numbers when the page opens and after every Refresh
    useEffect(() => {
        const fetchMetrics = async () => {
            try {
                const response = await api.get('/admin/assistant/metrics');
                setMetrics(response.data);
                setHasError(false);
            } catch (error) {
                console.error('Failed to fetch assistant metrics', error);
                setHasError(true);
            }
            setIsLoading(false);
        };
        fetchMetrics();
    }, [refreshCount]);

    // The Refresh button
    const loadMetrics = () => {
        setIsLoading(true);
        setRefreshCount(refreshCount + 1);
    };

    let content;
    if (hasError) {
        content = <p className="message-error">Couldn't load the assistant stats. Press Refresh to try again.</p>;
    } else if (metrics === null) {
        content = <p className="message-muted">Loading…</p>;
    } else {
        const totalAnswers = metrics.answersOk + metrics.answersFailed + metrics.answersProviderLimited;
        const totalRatings = metrics.thumbsUp + metrics.thumbsDown;

        let ratingNote = 'No ratings yet';
        if (totalRatings > 0) {
            ratingNote = Math.round((metrics.thumbsUp / totalRatings) * 100) + '% thumbs up';
        }

        content = (
            <>
                <MetricGroup title="Answers">
                    <MetricCard label="Answered" value={formatNumber(metrics.answersOk)} note={'of ' + formatNumber(totalAnswers) + ' questions'} />
                    <MetricCard label="Failed" value={formatNumber(metrics.answersFailed)} note="Model error or timeout" />
                    <MetricCard label="Groq limit reached" value={formatNumber(metrics.answersProviderLimited)} note="Groq's own usage limit" />
                    <MetricCard label="Average time" value={(metrics.averageLatencyMs / 1000).toFixed(1) + ' s'} note="Answers that worked" />
                </MetricGroup>

                <MetricGroup title="Usage">
                    <MetricCard label="Input tokens" value={formatNumber(metrics.inputTokens)} />
                    <MetricCard label="Output tokens" value={formatNumber(metrics.outputTokens)} />
                    <MetricCard label="Estimated cost" value={formatCost(metrics.estimatedCostUsd)} note="On a paid plan; the free tier is $0" />
                    <MetricCard label="Tool calls" value={formatNumber(metrics.toolCalls)} note={formatNumber(metrics.toolCallsNotOk) + ' not found or denied'} />
                </MetricGroup>

                <MetricGroup title="Safety">
                    <MetricCard label="Too many messages" value={formatNumber(metrics.blockedRateLimit)} note="Over 10 a minute" />
                    <MetricCard label="Tool limit hit" value={formatNumber(metrics.blockedToolLimit)} note="Over 5 tool calls in one answer" />
                    <MetricCard label="Hidden by output guard" value={formatNumber(metrics.blockedOutputGuard)} note="Account number, PIN or token removed" />
                </MetricGroup>

                <MetricGroup title="Confirm cards and feedback">
                    <MetricCard label="Freeze requests" value={formatNumber(metrics.actionsCreated)}
                        note={metrics.actionsConfirmed + ' confirmed, ' + metrics.actionsCancelled + ' cancelled, ' + metrics.actionsExpired + ' expired, ' + metrics.actionsFailed + ' failed'} />
                    <MetricCard label="Thumbs up" value={formatNumber(metrics.thumbsUp)} note={ratingNote} />
                    <MetricCard label="Thumbs down" value={formatNumber(metrics.thumbsDown)} />
                </MetricGroup>
            </>
        );
    }

    return (
        <>
            <header className="page-header">
                <div>
                    <p className="eyebrow">Admin</p>
                    <h1>Assistant stats</h1>
                    <p>
                        Totals for all customers
                        {metrics && <> since {formatDate(metrics.countingSince)} (the last restart)</>}.
                        No messages or account details are shown here.
                    </p>
                </div>
                <button type="button" onClick={loadMetrics} disabled={isLoading} className="button button--secondary">
                    {isLoading ? 'Loading…' : 'Refresh'}
                </button>
            </header>
            {content}
        </>
    );
};

export default AssistantMetricsPanel;
