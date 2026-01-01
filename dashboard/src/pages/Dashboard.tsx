import { useState, useEffect } from 'react'
import { TrendingUp, TrendingDown, Wallet, Calendar, ShoppingBag, Utensils, Car, Tv } from 'lucide-react'
import { PieChart, Pie, Cell, ResponsiveContainer, LineChart, Line, XAxis, YAxis, Tooltip, AreaChart, Area } from 'recharts'

interface DashboardData {
  totalSpentMonth: number
  totalSpentWeek: number
  totalSpentToday: number
  monthlyBudget: number
  budgetUsedPercent: number
  categoryBreakdown: CategoryBreakdown[]
  recentTransactions: Transaction[]
}

interface CategoryBreakdown {
  category: string
  amount: number
  count: number
  percentage: number
}

interface Transaction {
  id: string
  amount: number
  merchantName: string
  category: string
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

export default function Dashboard() {
  const [data, setData] = useState<DashboardData | null>(null)
  const [dailyStats, setDailyStats] = useState<{ date: string; amount: number }[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetchDashboardData()
    fetchDailyStats()
  }, [])

  const fetchDashboardData = async () => {
    try {
      const res = await fetch('/api/dashboard')
      const json = await res.json()
      if (json.success) {
        setData(json.data)
      } else {
        setError('Failed to load dashboard data')
      }
    } catch (err) {
      setError('Could not connect to server')
    } finally {
      setLoading(false)
    }
  }

  const fetchDailyStats = async () => {
    try {
      const res = await fetch('/api/stats/daily?days=14')
      const json = await res.json()
      if (json.success) {
        setDailyStats(json.data)
      }
    } catch (err) {
      console.error('Failed to fetch daily stats', err)
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary-500"></div>
      </div>
    )
  }

  if (error || !data) {
    return (
      <div className="card text-center py-12">
        <p className="text-gray-500">{error || 'No data available'}</p>
        <button onClick={fetchDashboardData} className="btn-primary mt-4">
          Retry
        </button>
      </div>
    )
  }

  const budgetRemaining = data.monthlyBudget - data.totalSpentMonth

  return (
    <div className="space-y-6 pb-20 md:pb-0">
      <h2 className="text-2xl font-bold text-gray-900">Dashboard</h2>

      {/* Total Spent Card */}
      <div className="bg-gradient-to-r from-primary-500 to-primary-600 rounded-2xl p-6 text-white">
        <p className="text-primary-100">Total Spent This Month</p>
        <p className="text-4xl font-bold mt-1">₹{data.totalSpentMonth.toLocaleString()}</p>
        <div className="flex items-center mt-2 text-primary-100">
          <TrendingUp size={16} className="mr-1" />
          <span>vs last month</span>
        </div>
      </div>

      {/* Quick Stats */}
      <div className="grid grid-cols-2 gap-4">
        <div className="stat-card">
          <div className="flex items-center text-gray-500 mb-1">
            <Calendar size={16} className="mr-2" />
            <span className="text-sm">Today</span>
          </div>
          <p className="text-xl font-semibold">₹{data.totalSpentToday.toLocaleString()}</p>
        </div>
        <div className="stat-card">
          <div className="flex items-center text-gray-500 mb-1">
            <Calendar size={16} className="mr-2" />
            <span className="text-sm">This Week</span>
          </div>
          <p className="text-xl font-semibold">₹{data.totalSpentWeek.toLocaleString()}</p>
        </div>
      </div>

      {/* Budget Progress */}
      <div className="card">
        <div className="flex justify-between items-center mb-3">
          <h3 className="font-semibold text-gray-900">Monthly Budget</h3>
          <span className={`text-sm font-medium ${
            data.budgetUsedPercent >= 100 ? 'text-red-500' :
            data.budgetUsedPercent >= 80 ? 'text-amber-500' : 'text-green-500'
          }`}>
            {data.budgetUsedPercent.toFixed(0)}% used
          </span>
        </div>
        <div className="w-full bg-gray-200 rounded-full h-3">
          <div
            className={`h-3 rounded-full transition-all ${
              data.budgetUsedPercent >= 100 ? 'bg-red-500' :
              data.budgetUsedPercent >= 80 ? 'bg-amber-500' : 'bg-green-500'
            }`}
            style={{ width: `${Math.min(data.budgetUsedPercent, 100)}%` }}
          ></div>
        </div>
        <p className="text-sm text-gray-500 mt-2">
          ₹{budgetRemaining.toLocaleString()} remaining of ₹{data.monthlyBudget.toLocaleString()}
        </p>
      </div>

      {/* Spending Trend Chart */}
      {dailyStats.length > 0 && (
        <div className="card">
          <h3 className="font-semibold text-gray-900 mb-4">Spending Trend (14 Days)</h3>
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={dailyStats}>
              <defs>
                <linearGradient id="colorAmount" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#10b981" stopOpacity={0.3}/>
                  <stop offset="95%" stopColor="#10b981" stopOpacity={0}/>
                </linearGradient>
              </defs>
              <XAxis
                dataKey="date"
                tick={{ fontSize: 12 }}
                tickFormatter={(value) => new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}
              />
              <YAxis tick={{ fontSize: 12 }} tickFormatter={(value) => `₹${value}`} />
              <Tooltip
                formatter={(value: number) => [`₹${value.toLocaleString()}`, 'Spent']}
                labelFormatter={(label) => new Date(label).toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' })}
              />
              <Area type="monotone" dataKey="amount" stroke="#10b981" fillOpacity={1} fill="url(#colorAmount)" />
            </AreaChart>
          </ResponsiveContainer>
        </div>
      )}

      {/* Category Breakdown */}
      <div className="card">
        <h3 className="font-semibold text-gray-900 mb-4">By Category</h3>
        <div className="flex flex-col md:flex-row items-center gap-6">
          <div className="w-48 h-48">
            <ResponsiveContainer width="100%" height="100%">
              <PieChart>
                <Pie
                  data={data.categoryBreakdown}
                  dataKey="amount"
                  nameKey="category"
                  cx="50%"
                  cy="50%"
                  innerRadius={40}
                  outerRadius={70}
                  paddingAngle={2}
                >
                  {data.categoryBreakdown.map((entry, index) => (
                    <Cell key={index} fill={CATEGORY_COLORS[entry.category] || '#6b7280'} />
                  ))}
                </Pie>
              </PieChart>
            </ResponsiveContainer>
          </div>
          <div className="flex-1 grid grid-cols-2 gap-3">
            {data.categoryBreakdown.slice(0, 6).map((cat) => (
              <div key={cat.category} className="flex items-center space-x-2">
                <div
                  className="w-3 h-3 rounded-full"
                  style={{ backgroundColor: CATEGORY_COLORS[cat.category] || '#6b7280' }}
                ></div>
                <div>
                  <p className="text-sm font-medium text-gray-700">{cat.category}</p>
                  <p className="text-xs text-gray-500">₹{cat.amount.toLocaleString()}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Recent Transactions */}
      <div className="card">
        <div className="flex justify-between items-center mb-4">
          <h3 className="font-semibold text-gray-900">Recent Transactions</h3>
          <a href="/dashboard/transactions" className="text-primary-500 text-sm hover:underline">
            View All
          </a>
        </div>
        <div className="space-y-3">
          {data.recentTransactions.slice(0, 5).map((tx) => (
            <div key={tx.id} className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
              <div className="flex items-center space-x-3">
                <div
                  className="w-10 h-10 rounded-full flex items-center justify-center"
                  style={{ backgroundColor: `${CATEGORY_COLORS[tx.category] || '#6b7280'}20` }}
                >
                  <span style={{ color: CATEGORY_COLORS[tx.category] || '#6b7280' }}>
                    {CATEGORY_ICONS[tx.category] || <Wallet size={16} />}
                  </span>
                </div>
                <div>
                  <p className="font-medium text-gray-900">{tx.merchantName}</p>
                  <p className="text-xs text-gray-500">{tx.category} • {tx.formattedDate}</p>
                </div>
              </div>
              <p className="font-semibold text-red-500">-₹{tx.amount.toLocaleString()}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
