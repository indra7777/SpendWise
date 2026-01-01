import { useState, useEffect } from 'react'
import { Search, Filter, Wallet, Utensils, ShoppingBag, Car, Tv } from 'lucide-react'

interface Transaction {
  id: string
  amount: number
  currency: string
  merchantName: string
  category: string
  subcategory: string | null
  timestamp: number
  formattedDate: string
}

const CATEGORY_COLORS: Record<string, string> = {
  FOOD: '#f97316',
  GROCERIES: '#22c55e',
  TRANSPORT: '#3b82f6',
  SHOPPING: '#ec4899',
  UTILITIES: '#8b5cf6',
  ENTERTAINMENT: '#f43f5e',
  HEALTH: '#14b8a6',
  TRANSFERS: '#6366f1',
  OTHER: '#6b7280',
}

const CATEGORY_ICONS: Record<string, React.ReactNode> = {
  FOOD: <Utensils size={16} />,
  SHOPPING: <ShoppingBag size={16} />,
  TRANSPORT: <Car size={16} />,
  ENTERTAINMENT: <Tv size={16} />,
}

export default function Transactions() {
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [loading, setLoading] = useState(true)
  const [searchQuery, setSearchQuery] = useState('')
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null)

  useEffect(() => {
    fetchTransactions()
  }, [])

  const fetchTransactions = async () => {
    try {
      const res = await fetch('/api/transactions?limit=100')
      const json = await res.json()
      if (json.success) {
        setTransactions(json.data)
      }
    } catch (err) {
      console.error('Failed to fetch transactions', err)
    } finally {
      setLoading(false)
    }
  }

  const filteredTransactions = transactions.filter(tx => {
    const matchesSearch = tx.merchantName.toLowerCase().includes(searchQuery.toLowerCase())
    const matchesCategory = !selectedCategory || tx.category === selectedCategory
    return matchesSearch && matchesCategory
  })

  const categories = [...new Set(transactions.map(tx => tx.category))]

  // Group transactions by date
  const groupedTransactions = filteredTransactions.reduce((groups, tx) => {
    const date = new Date(tx.timestamp).toLocaleDateString('en-IN', {
      weekday: 'long',
      day: 'numeric',
      month: 'long'
    })
    if (!groups[date]) {
      groups[date] = []
    }
    groups[date].push(tx)
    return groups
  }, {} as Record<string, Transaction[]>)

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-500"></div>
      </div>
    )
  }

  return (
    <div className="space-y-6 pb-20 md:pb-0">
      <h2 className="text-2xl font-bold text-gray-900">Transactions</h2>

      {/* Search and Filter */}
      <div className="space-y-4">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400" size={20} />
          <input
            type="text"
            placeholder="Search transactions..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-4 py-3 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
          />
        </div>

        {/* Category Filter */}
        <div className="flex flex-wrap gap-2">
          <button
            onClick={() => setSelectedCategory(null)}
            className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${
              !selectedCategory
                ? 'bg-primary-500 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            All
          </button>
          {categories.map(cat => (
            <button
              key={cat}
              onClick={() => setSelectedCategory(cat)}
              className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${
                selectedCategory === cat
                  ? 'text-white'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
              style={selectedCategory === cat ? { backgroundColor: CATEGORY_COLORS[cat] } : {}}
            >
              {cat}
            </button>
          ))}
        </div>
      </div>

      {/* Transaction List */}
      {Object.entries(groupedTransactions).length === 0 ? (
        <div className="card text-center py-12">
          <p className="text-gray-500">No transactions found</p>
        </div>
      ) : (
        Object.entries(groupedTransactions).map(([date, txs]) => (
          <div key={date} className="space-y-3">
            <h3 className="text-sm font-medium text-gray-500 sticky top-16 bg-gray-50 py-2">
              {date}
            </h3>
            <div className="card space-y-0 p-0 overflow-hidden">
              {txs.map((tx, index) => (
                <div
                  key={tx.id}
                  className={`flex items-center justify-between p-4 hover:bg-gray-50 transition-colors ${
                    index !== txs.length - 1 ? 'border-b border-gray-100' : ''
                  }`}
                >
                  <div className="flex items-center space-x-4">
                    <div
                      className="w-12 h-12 rounded-full flex items-center justify-center"
                      style={{ backgroundColor: `${CATEGORY_COLORS[tx.category] || '#6b7280'}15` }}
                    >
                      <span style={{ color: CATEGORY_COLORS[tx.category] || '#6b7280' }}>
                        {CATEGORY_ICONS[tx.category] || <Wallet size={20} />}
                      </span>
                    </div>
                    <div>
                      <p className="font-medium text-gray-900">{tx.merchantName}</p>
                      <p className="text-sm text-gray-500">
                        {tx.category}
                        {tx.subcategory && ` • ${tx.subcategory}`}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p className="font-semibold text-red-500">
                      -₹{tx.amount.toLocaleString()}
                    </p>
                    <p className="text-xs text-gray-400">
                      {new Date(tx.timestamp).toLocaleTimeString('en-IN', {
                        hour: '2-digit',
                        minute: '2-digit'
                      })}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </div>
        ))
      )}

      {/* Summary */}
      <div className="card bg-gray-50">
        <div className="flex justify-between items-center">
          <span className="text-gray-600">
            {filteredTransactions.length} transactions
          </span>
          <span className="font-semibold text-gray-900">
            Total: ₹{filteredTransactions.reduce((sum, tx) => sum + tx.amount, 0).toLocaleString()}
          </span>
        </div>
      </div>
    </div>
  )
}
